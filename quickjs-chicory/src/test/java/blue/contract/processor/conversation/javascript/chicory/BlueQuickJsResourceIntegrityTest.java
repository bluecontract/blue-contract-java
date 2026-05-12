package blue.contract.processor.conversation.javascript.chicory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BlueQuickJsResourceIntegrityTest {

    @Test
    void canonicalWasmResourceIsPresentAndPinned() {
        Path root = blueQuickJsRoot();
        BlueQuickJsWasmResources resources = BlueQuickJsWasmResources.resolve(
                BlueQuickJsWasmRuntimeConfig.builder()
                        .blueQuickJsRoot(root)
                        .preferClasspathResources(false)
                        .build());

        assertTrue(Files.isRegularFile(resources.wasmPath()));
        assertTrue(resources.wasmPath().getFileName().toString().equals(BlueQuickJsWasmResources.CANONICAL_WASM_FILENAME));
        assertEquals("1d4584fc0552a24ee840afa2cca9f1536d47429f467585d4d5c1a5236ba96dc9",
                resources.engineBuildHash());
        assertEquals(HostV1Manifest.HOST_V1_HASH, resources.abiManifestHash());
        assertEquals(BlueQuickJsWasmRuntimeConfig.DEFAULT_GAS_VERSION, resources.gasVersion());
        assertEquals(BlueQuickJsWasmRuntimeConfig.DEFAULT_EXECUTION_PROFILE, resources.executionProfile());
        assertEquals("wasm32", resources.metadata().path("variants").fieldNames().next());
        assertEquals("release", resources.metadata().path("variants").path("wasm32").path("release").path("buildType").asText());
        assertEquals("quickjs-eval.wasm",
                resources.metadata().path("variants").path("wasm32").path("release").path("wasm").path("filename").asText());
    }

    @Test
    void importsContainOnlyApprovedDeterministicSurface() {
        BlueQuickJsWasmResources resources = BlueQuickJsWasmResources.resolve(
                BlueQuickJsWasmRuntimeConfig.builder()
                        .blueQuickJsRoot(blueQuickJsRoot())
                        .preferClasspathResources(false)
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
    void requiredExportsArePresent() {
        BlueQuickJsWasmResources resources = BlueQuickJsWasmResources.resolve(
                BlueQuickJsWasmRuntimeConfig.builder()
                        .blueQuickJsRoot(blueQuickJsRoot())
                        .preferClasspathResources(false)
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
    void wrongExpectedEngineHashFailsClosed() {
        BlueQuickJsDeterminismException ex = assertThrows(BlueQuickJsDeterminismException.class,
                () -> BlueQuickJsWasmResources.resolve(
                        BlueQuickJsWasmRuntimeConfig.builder()
                                .blueQuickJsRoot(blueQuickJsRoot())
                                .preferClasspathResources(false)
                                .expectedEngineBuildHash("0000000000000000000000000000000000000000000000000000000000000000")
                                .build()));

        assertTrue(ex.getMessage().contains("engineBuildHash mismatch"));
    }

    private static Path blueQuickJsRoot() {
        String configured = System.getProperty("blue.quickjs.root");
        Path root = configured == null || configured.trim().isEmpty()
                ? Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent().resolve("blue-quickjs")
                : Paths.get(configured);
        assumeTrue(Files.isDirectory(root), "blue-quickjs checkout is required for resource integrity tests");
        return root;
    }
}
