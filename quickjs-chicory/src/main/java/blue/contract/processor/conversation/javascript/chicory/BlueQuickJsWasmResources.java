package blue.contract.processor.conversation.javascript.chicory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BlueQuickJsWasmResources {
    public static final String CANONICAL_WASM_FILENAME = "quickjs-eval.wasm";
    public static final String METADATA_FILENAME = "quickjs-wasm-build.metadata.json";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] WASM_MAGIC = new byte[]{0x00, 0x61, 0x73, 0x6d};
    private static final Set<String> APPROVED_IMPORTS = Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList(
                    "env.abort",
                    "env.__assert_fail",
                    "env.emscripten_date_now",
                    "env.emscripten_resize_heap",
                    "host.host_call")));

    private final Path blueQuickJsRoot;
    private final Path wasmPath;
    private final Path metadataPath;
    private final byte[] wasmBytes;
    private final JsonNode metadata;
    private final String engineBuildHash;
    private final String abiManifestHash;
    private final int gasVersion;
    private final String executionProfile;
    private final List<WasmImport> imports;
    private final List<WasmExport> exports;

    private BlueQuickJsWasmResources(Path blueQuickJsRoot,
                                     Path wasmPath,
                                     Path metadataPath,
                                     byte[] wasmBytes,
                                     JsonNode metadata,
                                     String engineBuildHash,
                                     String abiManifestHash,
                                     int gasVersion,
                                     String executionProfile,
                                     List<WasmImport> imports,
                                     List<WasmExport> exports) {
        this.blueQuickJsRoot = blueQuickJsRoot;
        this.wasmPath = wasmPath;
        this.metadataPath = metadataPath;
        this.wasmBytes = wasmBytes.clone();
        this.metadata = metadata;
        this.engineBuildHash = engineBuildHash;
        this.abiManifestHash = abiManifestHash;
        this.gasVersion = gasVersion;
        this.executionProfile = executionProfile;
        this.imports = Collections.unmodifiableList(new ArrayList<WasmImport>(imports));
        this.exports = Collections.unmodifiableList(new ArrayList<WasmExport>(exports));
    }

    public static BlueQuickJsWasmResources resolve() {
        return resolve(BlueQuickJsWasmRuntimeConfig.defaultConfig());
    }

    public static BlueQuickJsWasmResources resolve(BlueQuickJsWasmRuntimeConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (config.preferClasspathResources()) {
            BlueQuickJsWasmResources classpath = resolveClasspath(config);
            if (classpath != null) {
                return classpath;
            }
        }
        Path root = resolveRoot(config);
        Path wasmPath = locateWasm(root);
        Path metadataPath = locateMetadata(wasmPath, root);
        byte[] wasmBytes = readBytes(wasmPath);
        verifyMagic(wasmBytes, wasmPath);

        JsonNode metadata = readJson(metadataPath);
        JsonNode selected = selectedVariant(metadata, config);
        String metadataWasmFilename = requiredText(selected.at("/wasm/filename"),
                "metadata variants." + config.expectedVariant() + "." + config.expectedBuildType() + ".wasm.filename");
        if (!CANONICAL_WASM_FILENAME.equals(metadataWasmFilename)) {
            throw new BlueQuickJsDeterminismException("selected wasm is not canonical " + CANONICAL_WASM_FILENAME
                    + ": " + metadataWasmFilename);
        }
        String wasmSha256 = sha256Hex(wasmBytes);
        String metadataSha256 = requiredHex(selected.at("/wasm/sha256"), "metadata wasm sha256");
        if (!wasmSha256.equals(metadataSha256)) {
            throw new BlueQuickJsDeterminismException("wasm sha256 mismatch: metadata=" + metadataSha256
                    + ", actual=" + wasmSha256);
        }
        String engineBuildHash = requiredHex(selected.get("engineBuildHash"), "metadata engineBuildHash");
        if (!wasmSha256.equals(engineBuildHash)) {
            throw new BlueQuickJsDeterminismException("engineBuildHash does not match wasm sha256: engine="
                    + engineBuildHash + ", actual=" + wasmSha256);
        }
        String topLevelEngineBuildHash = requiredHex(metadata.get("engineBuildHash"), "metadata top-level engineBuildHash");
        if (!engineBuildHash.equals(topLevelEngineBuildHash)) {
            throw new BlueQuickJsDeterminismException("top-level engineBuildHash does not match selected engineBuildHash");
        }
        if (config.expectedEngineBuildHash() != null && !engineBuildHash.equals(config.expectedEngineBuildHash())) {
            throw new BlueQuickJsDeterminismException("engineBuildHash mismatch: expected="
                    + config.expectedEngineBuildHash() + ", actual=" + engineBuildHash);
        }
        verifyMetadataShape(metadata);
        String abiManifestHash = verifyAbiManifestHash(config);
        WasmModuleShape shape = WasmModuleShape.parse(wasmBytes);
        verifyImports(shape.imports);
        verifyExports(shape.exports);
        return new BlueQuickJsWasmResources(root,
                wasmPath,
                metadataPath,
                wasmBytes,
                metadata,
                engineBuildHash,
                abiManifestHash,
                config.expectedGasVersion(),
                config.expectedExecutionProfile(),
                shape.imports,
                shape.exports);
    }

    private static BlueQuickJsWasmResources resolveClasspath(BlueQuickJsWasmRuntimeConfig config) {
        String base = "/blue/contract/processor/quickjs/chicory/";
        try (InputStream wasmInput = BlueQuickJsWasmResources.class.getResourceAsStream(base + CANONICAL_WASM_FILENAME);
             InputStream metadataInput = BlueQuickJsWasmResources.class.getResourceAsStream(base + "engine-metadata.json")) {
            if (wasmInput == null || metadataInput == null) {
                return null;
            }
            byte[] wasmBytes = readAll(wasmInput);
            verifyMagic(wasmBytes, Paths.get("classpath:" + base + CANONICAL_WASM_FILENAME));
            JsonNode metadata = JSON.readTree(metadataInput);
            JsonNode selected = selectedVariant(metadata, config);
            String wasmSha256 = sha256Hex(wasmBytes);
            String metadataSha256 = requiredHex(selected.at("/wasm/sha256"), "metadata wasm sha256");
            String engineBuildHash = requiredHex(selected.get("engineBuildHash"), "metadata engineBuildHash");
            if (!wasmSha256.equals(metadataSha256) || !wasmSha256.equals(engineBuildHash)) {
                throw new BlueQuickJsDeterminismException("classpath wasm hash mismatch");
            }
            if (config.expectedEngineBuildHash() != null && !engineBuildHash.equals(config.expectedEngineBuildHash())) {
                throw new BlueQuickJsDeterminismException("engineBuildHash mismatch: expected="
                        + config.expectedEngineBuildHash() + ", actual=" + engineBuildHash);
            }
            verifyMetadataShape(metadata);
            int gasVersion = requiredInt(metadata.get("gasVersion"), "gasVersion");
            if (gasVersion != config.expectedGasVersion()) {
                throw new BlueQuickJsDeterminismException("gasVersion mismatch: expected="
                        + config.expectedGasVersion() + ", actual=" + gasVersion);
            }
            String executionProfile = requiredText(metadata.get("executionProfile"), "executionProfile");
            if (!config.expectedExecutionProfile().equals(executionProfile)) {
                throw new BlueQuickJsDeterminismException("executionProfile mismatch: expected="
                        + config.expectedExecutionProfile() + ", actual=" + executionProfile);
            }
            String abiManifestHash = requiredText(metadata.get("abiManifestHash"), "abiManifestHash");
            if (!HostV1Manifest.HOST_V1_HASH.equals(abiManifestHash)) {
                throw new BlueQuickJsDeterminismException("ABI manifest hash mismatch in classpath metadata");
            }
            WasmModuleShape shape = WasmModuleShape.parse(wasmBytes);
            verifyImports(shape.imports);
            verifyExports(shape.exports);
            return new BlueQuickJsWasmResources(null, null, null, wasmBytes, metadata,
                    engineBuildHash, abiManifestHash, gasVersion, executionProfile, shape.imports, shape.exports);
        } catch (IOException ex) {
            throw new BlueQuickJsResourceException("failed to read classpath blue-quickjs resources", ex);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public Path blueQuickJsRoot() {
        return blueQuickJsRoot;
    }

    public Path wasmPath() {
        return wasmPath;
    }

    public Path metadataPath() {
        return metadataPath;
    }

    public byte[] wasmBytes() {
        return wasmBytes.clone();
    }

    public JsonNode metadata() {
        return metadata;
    }

    public String engineBuildHash() {
        return engineBuildHash;
    }

    public String abiManifestHash() {
        return abiManifestHash;
    }

    public int gasVersion() {
        return gasVersion;
    }

    public String executionProfile() {
        return executionProfile;
    }

    public List<WasmImport> imports() {
        return imports;
    }

    public List<WasmExport> exports() {
        return exports;
    }

    private static Path resolveRoot(BlueQuickJsWasmRuntimeConfig config) {
        Path configured = config.blueQuickJsRoot();
        if (configured == null) {
            String property = System.getProperty(BlueQuickJsWasmRuntimeConfig.BLUE_QUICKJS_ROOT_PROPERTY);
            if (property != null && !property.trim().isEmpty()) {
                configured = Paths.get(property);
            }
        }
        if (configured == null) {
            configured = Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent().resolve("blue-quickjs");
        }
        Path root = configured.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new BlueQuickJsResourceException("blue-quickjs root not found: " + root);
        }
        return root;
    }

    private static Path locateWasm(Path root) {
        List<Path> candidates = Arrays.asList(
                root.resolve("libs/quickjs-wasm/dist/wasm").resolve(CANONICAL_WASM_FILENAME),
                root.resolve("libs/quickjs-wasm-build/dist").resolve(CANONICAL_WASM_FILENAME));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains("debug") || name.contains("wasm64")) {
                    throw new BlueQuickJsDeterminismException("rejected non-release/non-wasm32 artifact: " + candidate);
                }
                return candidate;
            }
        }
        throw new BlueQuickJsResourceException("canonical wasm32 release artifact missing under " + root);
    }

    private static Path locateMetadata(Path wasmPath, Path root) {
        List<Path> candidates = Arrays.asList(
                wasmPath.getParent().resolve(METADATA_FILENAME),
                root.resolve("libs/quickjs-wasm-build/dist").resolve(METADATA_FILENAME));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new BlueQuickJsResourceException("blue-quickjs wasm metadata missing for " + wasmPath);
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new BlueQuickJsResourceException("failed to read " + path, ex);
        }
    }

    private static JsonNode readJson(Path path) {
        try {
            return JSON.readTree(Files.newBufferedReader(path, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new BlueQuickJsResourceException("failed to parse metadata " + path, ex);
        }
    }

    private static void verifyMagic(byte[] bytes, Path path) {
        if (bytes.length < 8) {
            throw new BlueQuickJsDeterminismException("wasm artifact is too small: " + path);
        }
        for (int i = 0; i < WASM_MAGIC.length; i++) {
            if (bytes[i] != WASM_MAGIC[i]) {
                throw new BlueQuickJsDeterminismException("invalid wasm magic bytes for " + path);
            }
        }
    }

    private static JsonNode selectedVariant(JsonNode metadata, BlueQuickJsWasmRuntimeConfig config) {
        JsonNode selected = metadata.path("variants")
                .path(config.expectedVariant())
                .path(config.expectedBuildType());
        if (selected.isMissingNode() || selected.isNull()) {
            throw new BlueQuickJsDeterminismException("metadata missing "
                    + config.expectedVariant() + "/" + config.expectedBuildType() + " artifact");
        }
        String buildType = requiredText(selected.get("buildType"), "metadata buildType");
        if (!config.expectedBuildType().equals(buildType)) {
            throw new BlueQuickJsDeterminismException("buildType mismatch: expected="
                    + config.expectedBuildType() + ", actual=" + buildType);
        }
        if (!"wasm32".equals(config.expectedVariant())) {
            throw new BlueQuickJsDeterminismException("only wasm32 is supported: " + config.expectedVariant());
        }
        if (!"release".equals(config.expectedBuildType())) {
            throw new BlueQuickJsDeterminismException("only release artifacts are supported: " + config.expectedBuildType());
        }
        return selected;
    }

    private static void verifyMetadataShape(JsonNode metadata) {
        JsonNode memory = metadata.path("build").path("memory");
        if (requiredInt(memory.get("initial"), "memory.initial") != 33554432) {
            throw new BlueQuickJsDeterminismException("unexpected initial wasm memory");
        }
        if (requiredInt(memory.get("maximum"), "memory.maximum") != 33554432) {
            throw new BlueQuickJsDeterminismException("unexpected maximum wasm memory");
        }
        if (requiredInt(memory.get("stackSize"), "memory.stackSize") != 1048576) {
            throw new BlueQuickJsDeterminismException("unexpected wasm stack size");
        }
        if (memory.path("allowGrowth").asBoolean(true)) {
            throw new BlueQuickJsDeterminismException("wasm memory growth must be disabled");
        }
        JsonNode flags = metadata.path("build").path("determinism").path("flags");
        if (!flags.isArray()) {
            throw new BlueQuickJsDeterminismException("metadata determinism flags are missing");
        }
        requireFlag(flags, "-sFILESYSTEM=0");
        requireFlag(flags, "-sALLOW_MEMORY_GROWTH=0");
        requireFlag(flags, "-sALLOW_TABLE_GROWTH=0");
    }

    private static void requireFlag(JsonNode flags, String expected) {
        for (JsonNode flag : flags) {
            if (expected.equals(flag.asText())) {
                return;
            }
        }
        throw new BlueQuickJsDeterminismException("metadata missing deterministic flag " + expected);
    }

    private static String verifyAbiManifestHash(BlueQuickJsWasmRuntimeConfig config) {
        String actual = HostV1Manifest.HOST_V1_HASH;
        String expected = config.expectedAbiManifestHash();
        if (expected == null || expected.trim().isEmpty()) {
            throw new BlueQuickJsDeterminismException("expected ABI manifest hash is required");
        }
        if (!actual.equals(expected)) {
            throw new BlueQuickJsDeterminismException("ABI manifest hash mismatch: expected="
                    + expected + ", actual=" + actual);
        }
        return actual;
    }

    private static void verifyImports(List<WasmImport> imports) {
        for (WasmImport wasmImport : imports) {
            String key = wasmImport.module() + "." + wasmImport.name();
            if (!APPROVED_IMPORTS.contains(key)) {
                throw new BlueQuickJsDeterminismException("unsupported wasm import: " + key);
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith("wasi_") || lower.contains("random") || lower.contains("clock")
                    || lower.contains("fd_") || lower.contains("sock")) {
                throw new BlueQuickJsDeterminismException("nondeterministic wasm import rejected: " + key);
            }
        }
    }

    private static void verifyExports(List<WasmExport> exports) {
        Set<String> names = new LinkedHashSet<String>();
        for (WasmExport wasmExport : exports) {
            names.add(wasmExport.name());
        }
        for (String required : Arrays.asList("memory", "malloc", "free", "qjs_det_init", "qjs_det_eval",
                "qjs_det_set_gas_limit", "qjs_det_free")) {
            if (!names.contains(required)) {
                throw new BlueQuickJsDeterminismException("required wasm export missing: " + required);
            }
        }
    }

    private static String requiredText(JsonNode node, String path) {
        if (node == null || !node.isTextual() || node.asText().trim().isEmpty()) {
            throw new BlueQuickJsDeterminismException("required metadata field missing or non-text: " + path);
        }
        return node.asText();
    }

    private static String requiredHex(JsonNode node, String path) {
        String value = requiredText(node, path).toLowerCase(Locale.ROOT);
        if (value.length() != 64) {
            throw new BlueQuickJsDeterminismException(path + " must be a SHA-256 hex string");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                throw new BlueQuickJsDeterminismException(path + " contains non-hex characters");
            }
        }
        return value;
    }

    private static int requiredInt(JsonNode node, String path) {
        if (node == null || !node.canConvertToInt()) {
            throw new BlueQuickJsDeterminismException("required metadata integer missing: " + path);
        }
        return node.asInt();
    }

    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BlueQuickJsResourceException("SHA-256 is unavailable", ex);
        }
    }

    public static final class WasmImport {
        private final String module;
        private final String name;
        private final String kind;
        private final String signature;

        private WasmImport(String module, String name, String kind, String signature) {
            this.module = module;
            this.name = name;
            this.kind = kind;
            this.signature = signature;
        }

        public String module() {
            return module;
        }

        public String name() {
            return name;
        }

        public String kind() {
            return kind;
        }

        public String signature() {
            return signature;
        }
    }

    public static final class WasmExport {
        private final String name;
        private final String kind;
        private final int index;

        private WasmExport(String name, String kind, int index) {
            this.name = name;
            this.kind = kind;
            this.index = index;
        }

        public String name() {
            return name;
        }

        public String kind() {
            return kind;
        }

        public int index() {
            return index;
        }
    }

    private static final class WasmModuleShape {
        private final List<WasmImport> imports;
        private final List<WasmExport> exports;

        private WasmModuleShape(List<WasmImport> imports, List<WasmExport> exports) {
            this.imports = imports;
            this.exports = exports;
        }

        private static WasmModuleShape parse(byte[] bytes) {
            WasmReader reader = new WasmReader(bytes);
            reader.expectHeader();
            List<FuncType> types = new ArrayList<FuncType>();
            List<WasmImport> imports = new ArrayList<WasmImport>();
            List<WasmExport> exports = new ArrayList<WasmExport>();
            while (!reader.done()) {
                int sectionId = reader.u8();
                int sectionSize = reader.varuint32();
                int sectionEnd = reader.position() + sectionSize;
                if (sectionId == 1) {
                    int count = reader.varuint32();
                    for (int i = 0; i < count; i++) {
                        reader.expectByte(0x60);
                        int paramCount = reader.varuint32();
                        List<String> params = new ArrayList<String>();
                        for (int p = 0; p < paramCount; p++) {
                            params.add(valType(reader.u8()));
                        }
                        int resultCount = reader.varuint32();
                        List<String> results = new ArrayList<String>();
                        for (int r = 0; r < resultCount; r++) {
                            results.add(valType(reader.u8()));
                        }
                        types.add(new FuncType(params, results));
                    }
                } else if (sectionId == 2) {
                    int count = reader.varuint32();
                    for (int i = 0; i < count; i++) {
                        String module = reader.name();
                        String name = reader.name();
                        int kind = reader.u8();
                        imports.add(readImport(reader, types, module, name, kind));
                    }
                } else if (sectionId == 7) {
                    int count = reader.varuint32();
                    for (int i = 0; i < count; i++) {
                        String name = reader.name();
                        int kind = reader.u8();
                        int index = reader.varuint32();
                        exports.add(new WasmExport(name, externalKind(kind), index));
                    }
                }
                reader.position(sectionEnd);
            }
            return new WasmModuleShape(imports, exports);
        }

        private static WasmImport readImport(WasmReader reader,
                                             List<FuncType> types,
                                             String module,
                                             String name,
                                             int kind) {
            if (kind == 0) {
                int typeIndex = reader.varuint32();
                String signature = typeIndex >= 0 && typeIndex < types.size()
                        ? types.get(typeIndex).signature()
                        : "type[" + typeIndex + "]";
                return new WasmImport(module, name, "func", signature);
            }
            if (kind == 1) {
                reader.u8();
                skipLimits(reader);
                return new WasmImport(module, name, "table", "");
            }
            if (kind == 2) {
                skipLimits(reader);
                return new WasmImport(module, name, "memory", "");
            }
            if (kind == 3) {
                String type = valType(reader.u8());
                int mutable = reader.u8();
                return new WasmImport(module, name, "global", type + " mutable=" + mutable);
            }
            throw new BlueQuickJsDeterminismException("unsupported wasm import kind: " + kind);
        }

        private static void skipLimits(WasmReader reader) {
            int flags = reader.u8();
            reader.varuint32();
            if ((flags & 1) != 0) {
                reader.varuint32();
            }
        }

        private static String valType(int type) {
            switch (type) {
                case 0x7f:
                    return "i32";
                case 0x7e:
                    return "i64";
                case 0x7d:
                    return "f32";
                case 0x7c:
                    return "f64";
                default:
                    return "0x" + Integer.toHexString(type);
            }
        }

        private static String externalKind(int kind) {
            switch (kind) {
                case 0:
                    return "func";
                case 1:
                    return "table";
                case 2:
                    return "memory";
                case 3:
                    return "global";
                default:
                    return "kind-" + kind;
            }
        }
    }

    private static final class FuncType {
        private final List<String> params;
        private final List<String> results;

        private FuncType(List<String> params, List<String> results) {
            this.params = params;
            this.results = results;
        }

        private String signature() {
            return "(" + join(params) + ") -> " + (results.isEmpty() ? "()" : join(results));
        }

        private static String join(List<String> values) {
            if (values.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(values.get(i));
            }
            return builder.toString();
        }
    }

    private static final class WasmReader {
        private final byte[] bytes;
        private int position;

        private WasmReader(byte[] bytes) {
            this.bytes = bytes;
        }

        private void expectHeader() {
            if (bytes.length < 8 || bytes[0] != 0x00 || bytes[1] != 0x61 || bytes[2] != 0x73 || bytes[3] != 0x6d) {
                throw new BlueQuickJsDeterminismException("invalid wasm header");
            }
            position = 8;
        }

        private boolean done() {
            return position >= bytes.length;
        }

        private int position() {
            return position;
        }

        private void position(int position) {
            if (position < 0 || position > bytes.length) {
                throw new BlueQuickJsDeterminismException("invalid wasm section size");
            }
            this.position = position;
        }

        private int u8() {
            if (position >= bytes.length) {
                throw new BlueQuickJsDeterminismException("unexpected end of wasm");
            }
            return bytes[position++] & 0xff;
        }

        private void expectByte(int expected) {
            int actual = u8();
            if (actual != expected) {
                throw new BlueQuickJsDeterminismException("unexpected wasm byte: expected "
                        + expected + ", actual " + actual);
            }
        }

        private int varuint32() {
            long result = 0L;
            int shift = 0;
            for (int i = 0; i < 5; i++) {
                int value = u8();
                result |= (long) (value & 0x7f) << shift;
                if ((value & 0x80) == 0) {
                    if (result > 0xffffffffL) {
                        throw new BlueQuickJsDeterminismException("wasm varuint32 overflow");
                    }
                    return (int) result;
                }
                shift += 7;
            }
            throw new BlueQuickJsDeterminismException("wasm varuint32 too long");
        }

        private String name() {
            int length = varuint32();
            if (length < 0 || length > bytes.length - position) {
                throw new BlueQuickJsDeterminismException("invalid wasm name length");
            }
            String value = new String(bytes, position, length, StandardCharsets.UTF_8);
            position += length;
            return value;
        }
    }
}
