package blue.contract.processor.conversation.javascript.chicory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class ChicoryTestSupport {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ChicoryTestSupport() {
    }

    static Path blueQuickJsRoot(String reason) {
        String configured = System.getProperty("blue.quickjs.root");
        Path root;
        if (configured == null || configured.trim().isEmpty()) {
            Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            root = cwd.getParent().resolve("blue-quickjs");
            if (!Files.isDirectory(root) && cwd.getParent() != null && cwd.getParent().getParent() != null) {
                root = cwd.getParent().getParent().resolve("blue-quickjs");
            }
        } else {
            root = Paths.get(configured);
        }
        assumeTrue(Files.isDirectory(root), reason);
        return root;
    }

    static BlueQuickJsWasmRuntimeConfig pinnedConfig(Path root) {
        return BlueQuickJsWasmRuntimeConfig.builder()
                .blueQuickJsRoot(root)
                .expectedEngineBuildHash(engineBuildHash(root))
                .build();
    }

    static String engineBuildHash(Path root) {
        return metadata(root)
                .path("variants")
                .path("wasm32")
                .path("release")
                .path("engineBuildHash")
                .asText();
    }

    static int gasVersion(Path root) {
        JsonNode gasVersion = metadata(root).get("gasVersion");
        return gasVersion != null && gasVersion.isNumber()
                ? gasVersion.asInt()
                : BlueQuickJsWasmRuntimeConfig.DEFAULT_GAS_VERSION;
    }

    static JsonNode metadata(Path root) {
        Path metadata = root.resolve("libs/quickjs-wasm/dist/wasm").resolve(BlueQuickJsWasmResources.METADATA_FILENAME);
        if (!Files.isRegularFile(metadata)) {
            metadata = root.resolve("libs/quickjs-wasm-build/dist").resolve(BlueQuickJsWasmResources.METADATA_FILENAME);
        }
        assumeTrue(Files.isRegularFile(metadata), "blue-quickjs wasm metadata is required");
        try {
            return JSON.readTree(metadata.toFile());
        } catch (IOException ex) {
            throw new AssertionError("failed to read blue-quickjs metadata: " + metadata, ex);
        }
    }

    static Path reportPath(String fileName) {
        return Paths.get(System.getProperty("user.dir"), "build/reports", fileName);
    }
}
