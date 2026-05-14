package blue.contract.processor.conversation.javascript;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeQuickJsRuntimeTest {

    @Test
    void vmErrorWithGasPreservesGasMetadata() {
        NodeQuickJsRuntime runtime = runtime();

        JavaScriptExecutionException ex = assertThrows(JavaScriptExecutionException.class,
                () -> runtime.parseResult("{\"ok\":false,\"type\":\"vm-error\",\"message\":\"boom\",\"wasmGasUsed\":\"1700\"}"));

        assertEquals("vm-error: boom", ex.getMessage());
        assertTrue(ex.hasGasUsage());
        assertEquals(1700L, ex.wasmGasUsed());
        assertEquals(1L, ex.hostGasUsed());
    }

    @Test
    void bridgeSetupErrorWithoutGasPreservesMessageWithoutFabricatingGas() {
        NodeQuickJsRuntime runtime = runtime();

        JavaScriptExecutionException ex = assertThrows(JavaScriptExecutionException.class,
                () -> runtime.parseResult("{\"ok\":false,\"message\":\"blue-quickjs root argument is required\"}"));

        assertEquals("blue-quickjs root argument is required", ex.getMessage());
        assertFalse(ex.hasGasUsage());
    }

    @Test
    void successfulResponseMissingGasIsMalformed() {
        NodeQuickJsRuntime runtime = runtime();

        JavaScriptExecutionException ex = assertThrows(JavaScriptExecutionException.class,
                () -> runtime.parseResult("{\"ok\":true,\"value\":1}"));

        assertTrue(ex.getMessage().contains("QuickJS bridge returned invalid wasmGasUsed"));
    }

    @Test
    void malformedGasValueIsRejected() {
        NodeQuickJsRuntime runtime = runtime();

        JavaScriptExecutionException ex = assertThrows(JavaScriptExecutionException.class,
                () -> runtime.parseResult("{\"ok\":false,\"message\":\"boom\",\"wasmGasUsed\":\"not-a-number\"}"));

        assertTrue(ex.getMessage().contains("QuickJS bridge returned invalid wasmGasUsed"));
    }

    private static NodeQuickJsRuntime runtime() {
        return new NodeQuickJsRuntime(Paths.get("."), Paths.get("."), 1000L);
    }
}
