package blue.contract.processor.conversation.javascript.chicory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BlueQuickJsResourceIntegrityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void canonicalWasmResourceIsPresentAndPinned(@TempDir Path tempDir) throws IOException {
        Path root = pinnedFilesystemFixture(blueQuickJsRoot(), tempDir);
        BlueQuickJsWasmResources resources = BlueQuickJsWasmResources.resolve(
                BlueQuickJsWasmRuntimeConfig.builder()
                        .blueQuickJsRoot(root)
                        .preferClasspathResources(false)
                        .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                        .expectedGasVersion(ChicoryTestSupport.gasVersion(root))
                        .build());

        assertTrue(Files.isRegularFile(resources.wasmPath()));
        assertTrue(resources.wasmPath().getFileName().toString().equals(BlueQuickJsWasmResources.CANONICAL_WASM_FILENAME));
        assertEquals(ChicoryTestSupport.engineBuildHash(root), resources.engineBuildHash());
        assertEquals(HostV1Manifest.HOST_V1_HASH, resources.abiManifestHash());
        assertEquals(ChicoryTestSupport.gasVersion(root), resources.gasVersion());
        assertEquals(BlueQuickJsWasmRuntimeConfig.DEFAULT_EXECUTION_PROFILE, resources.executionProfile());
        assertEquals("wasm32", resources.metadata().path("variants").fieldNames().next());
        assertEquals("release", resources.metadata().path("variants").path("wasm32").path("release").path("buildType").asText());
        assertEquals("quickjs-eval.wasm",
                resources.metadata().path("variants").path("wasm32").path("release").path("wasm").path("filename").asText());
    }

    @Test
    void classpathBundledResourceMetadataIsSelfPinned() {
        BlueQuickJsWasmResources resources = BlueQuickJsWasmResources.resolve(
                BlueQuickJsWasmRuntimeConfig.builder()
                        .blueQuickJsRoot(null)
                        .preferClasspathResources(true)
                        .build());

        assertEquals(HostV1Manifest.HOST_V1_HASH, resources.abiManifestHash());
        assertEquals(BlueQuickJsWasmRuntimeConfig.DEFAULT_GAS_VERSION, resources.gasVersion());
        assertEquals(BlueQuickJsWasmRuntimeConfig.DEFAULT_EXECUTION_PROFILE, resources.executionProfile());
        assertEquals(resources.metadata().path("engineBuildHash").asText(), resources.engineBuildHash());
    }

    @Test
    void importsContainOnlyApprovedDeterministicSurface(@TempDir Path tempDir) throws IOException {
        Path root = pinnedFilesystemFixture(blueQuickJsRoot(), tempDir);
        BlueQuickJsWasmResources resources = BlueQuickJsWasmResources.resolve(
                BlueQuickJsWasmRuntimeConfig.builder()
                        .blueQuickJsRoot(root)
                        .preferClasspathResources(false)
                        .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                        .build());
        Set<String> imports = new HashSet<String>();
        for (BlueQuickJsWasmResources.WasmImport wasmImport : resources.imports()) {
            imports.add(wasmImport.module() + "." + wasmImport.name());
            String lower = (wasmImport.module() + "." + wasmImport.name()).toLowerCase();
            assertFalse(lower.contains("random"));
            assertFalse(lower.contains("clock"));
            assertFalse(lower.contains("fd_"));
            assertFalse(lower.contains("sock"));
            assertFalse(lower.startsWith("wasi_"));
        }

        assertTrue(imports.contains("host.host_call"));
        assertTrue(imports.contains("env.abort"));
        assertTrue(imports.contains("env.__assert_fail"));
        assertTrue(imports.contains("env.emscripten_date_now"));
        assertTrue(imports.contains("env.emscripten_resize_heap"));
        assertEquals(5, imports.size());
    }

    @Test
    void requiredExportsArePresent(@TempDir Path tempDir) throws IOException {
        Path root = pinnedFilesystemFixture(blueQuickJsRoot(), tempDir);
        BlueQuickJsWasmResources resources = BlueQuickJsWasmResources.resolve(
                BlueQuickJsWasmRuntimeConfig.builder()
                        .blueQuickJsRoot(root)
                        .preferClasspathResources(false)
                        .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                        .build());
        Set<String> exports = new HashSet<String>();
        for (BlueQuickJsWasmResources.WasmExport wasmExport : resources.exports()) {
            exports.add(wasmExport.name());
        }

        assertTrue(exports.contains("memory"));
        assertTrue(exports.contains("malloc"));
        assertTrue(exports.contains("free"));
        assertTrue(exports.contains("qjs_det_init"));
        assertTrue(exports.contains("qjs_det_eval"));
        assertTrue(exports.contains("qjs_det_set_gas_limit"));
        assertTrue(exports.contains("qjs_det_free"));
    }

    @Test
    void missingExpectedEngineHashForFilesystemRuntimeFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = pinnedFilesystemFixture(blueQuickJsRoot(), tempDir);
        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .build()));

        assertTrue(ex.getMessage().contains("expected engineBuildHash is required"));
    }

    @Test
    void wrongExpectedEngineHashFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = pinnedFilesystemFixture(blueQuickJsRoot(), tempDir);
        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash("0000000000000000000000000000000000000000000000000000000000000000")
                                .build()));

        assertTrue(ex.getMessage().contains("engineBuildHash mismatch"));
    }

    @Test
    void missingFilesystemEngineHashFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = filesystemFixture(blueQuickJsRoot(), tempDir, metadata -> metadata.remove("engineBuildHash"));

        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(blueQuickJsRoot()))
                                .build()));

        assertTrue(ex.getMessage().contains("engineBuildHash"));
    }

    @Test
    void missingFilesystemGasVersionFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = filesystemFixture(blueQuickJsRoot(), tempDir, metadata -> metadata.remove("gasVersion"));

        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                                .build()));

        assertTrue(ex.getMessage().contains("gasVersion"));
    }

    @Test
    void missingFilesystemExecutionProfileFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = filesystemFixture(blueQuickJsRoot(), tempDir, metadata -> metadata.remove("executionProfile"));

        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                                .build()));

        assertTrue(ex.getMessage().contains("executionProfile"));
    }

    @Test
    void missingFilesystemAbiManifestHashFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = filesystemFixture(blueQuickJsRoot(), tempDir, metadata -> metadata.remove("abiManifestHash"));

        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                                .build()));

        assertTrue(ex.getMessage().contains("abiManifestHash"));
    }

    @Test
    void wrongFilesystemExecutionProfileFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = filesystemFixture(blueQuickJsRoot(), tempDir,
                metadata -> metadata.put("executionProfile", "different-profile"));

        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                                .build()));

        assertTrue(ex.getMessage().contains("executionProfile mismatch"));
    }

    @Test
    void filesystemWasmHashMismatchFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = filesystemFixture(blueQuickJsRoot(), tempDir, metadata -> ((ObjectNode) metadata
                .path("variants")
                .path("wasm32")
                .path("release")
                .path("wasm"))
                .put("sha256", "0000000000000000000000000000000000000000000000000000000000000000"));

        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                                .build()));

        assertTrue(ex.getMessage().contains("wasm sha256 mismatch"));
    }

    @Test
    void wrongHostV1HashFailsClosed() {
        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(null)
                                .preferClasspathResources(true)
                                .expectedAbiManifestHash("0000000000000000000000000000000000000000000000000000000000000000")
                                .build()));

        assertTrue(ex.getMessage().contains("ABI manifest hash mismatch"));
    }

    @Test
    void wrongGasVersionFailsClosed() {
        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(null)
                                .preferClasspathResources(true)
                                .expectedGasVersion(BlueQuickJsWasmRuntimeConfig.DEFAULT_GAS_VERSION + 1)
                                .build()));

        assertTrue(ex.getMessage().contains("gasVersion mismatch"));
    }

    @Test
    void wrongExecutionProfileFailsClosed() {
        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(null)
                                .preferClasspathResources(true)
                                .expectedExecutionProfile("compat-general-v1")
                                .build()));

        assertTrue(ex.getMessage().contains("executionProfile mismatch"));
    }

    @Test
    void growingFilesystemMemoryFailsClosed(@TempDir Path tempDir) throws IOException {
        Path root = filesystemFixture(blueQuickJsRoot(), tempDir,
                metadata -> ((ObjectNode) metadata.path("build").path("memory")).put("allowGrowth", true));

        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                                .build()));

        assertTrue(ex.getMessage().contains("memory growth must be disabled"));
    }

    @Test
    void filesystemMemoryFlagsMustMatchMetadata(@TempDir Path tempDir) throws IOException {
        Path root = filesystemFixture(blueQuickJsRoot(), tempDir,
                metadata -> ((ObjectNode) metadata.path("build").path("memory")).put("initial", 67108864));

        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(root)
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash(ChicoryTestSupport.engineBuildHash(root))
                                .build()));

        assertTrue(ex.getMessage().contains("wasm memory must be fixed")
                || ex.getMessage().contains("metadata missing deterministic flag"));
    }

    private static Path blueQuickJsRoot() {
        return ChicoryTestSupport.blueQuickJsRoot("blue-quickjs checkout is required for resource integrity tests");
    }

    private static Path pinnedFilesystemFixture(Path sourceRoot, Path tempDir) throws IOException {
        return filesystemFixture(sourceRoot, tempDir, metadata -> {
        });
    }

    private static Path filesystemFixture(Path sourceRoot,
                                          Path tempDir,
                                          MetadataMutation mutation) throws IOException {
        Path fixtureRoot = tempDir.resolve("blue-quickjs");
        Path wasmDir = fixtureRoot.resolve("libs/quickjs-wasm/dist/wasm");
        Files.createDirectories(wasmDir);
        Files.copy(sourceWasm(sourceRoot),
                wasmDir.resolve(BlueQuickJsWasmResources.CANONICAL_WASM_FILENAME),
                StandardCopyOption.REPLACE_EXISTING);

        ObjectNode metadata = (ObjectNode) JSON.readTree(sourceMetadata(sourceRoot).toFile());
        if (!metadata.has("gasVersion")) {
            metadata.put("gasVersion", BlueQuickJsWasmRuntimeConfig.DEFAULT_GAS_VERSION);
        }
        metadata.put("executionProfile", BlueQuickJsWasmRuntimeConfig.DEFAULT_EXECUTION_PROFILE);
        metadata.put("abiManifestHash", HostV1Manifest.HOST_V1_HASH);
        mutation.mutate(metadata);
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                wasmDir.resolve(BlueQuickJsWasmResources.METADATA_FILENAME).toFile(),
                metadata);
        return fixtureRoot;
    }

    private static Path sourceWasm(Path root) {
        Path wasm = root.resolve("libs/quickjs-wasm/dist/wasm").resolve(BlueQuickJsWasmResources.CANONICAL_WASM_FILENAME);
        assumeTrue(Files.isRegularFile(wasm), "canonical wasm is required for resource integrity tests");
        return wasm;
    }

    private static Path sourceMetadata(Path root) {
        Path metadata = root.resolve("libs/quickjs-wasm/dist/wasm").resolve(BlueQuickJsWasmResources.METADATA_FILENAME);
        if (Files.isRegularFile(metadata)) {
            return metadata;
        }
        metadata = root.resolve("libs/quickjs-wasm-build/dist").resolve(BlueQuickJsWasmResources.METADATA_FILENAME);
        assumeTrue(Files.isRegularFile(metadata), "wasm metadata is required for resource integrity tests");
        return metadata;
    }

    private interface MetadataMutation {
        void mutate(ObjectNode metadata);
    }
}
