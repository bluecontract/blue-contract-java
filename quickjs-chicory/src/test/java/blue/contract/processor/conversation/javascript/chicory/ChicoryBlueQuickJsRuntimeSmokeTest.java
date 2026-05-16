package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChicoryBlueQuickJsRuntimeSmokeTest {
    private static final int SMOKE_REPETITIONS = 2;

    @Test
    void deterministicExpressionsEvaluateWithoutNode() {
        BlueQuickJsWasmRuntimeConfig config = ChicoryTestSupport.pinnedConfig(blueQuickJsRoot());
        ChicoryBlueQuickJsRuntime runtime = new ChicoryBlueQuickJsRuntime(config);

        assertStable(runtime, "1 + 2", 3);
        assertStable(runtime, "\"blue\" + \"-quickjs\"", "blue-quickjs");

        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("a", 1);
        object.put("b", Arrays.asList(Boolean.TRUE, null));
        assertStable(runtime, "({ a: 1, b: [true, null] })", object);
        assertStable(runtime, "[1, 2, 3].map(x => x + 1)", Arrays.asList(2, 3, 4));
    }

    private static void assertStable(ChicoryBlueQuickJsRuntime runtime, String code, Object expected) {
        Long wasmGas = null;
        Long hostGas = null;
        for (int i = 0; i < SMOKE_REPETITIONS; i++) {
            JavaScriptEvaluationResult result = runtime.evaluate(new JavaScriptEvaluationRequest(
                    code,
                    JavaScriptEvaluationRequest.Mode.EXPRESSION,
                    Collections.<String, Object>emptyMap(),
                    QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT));
            assertEquals(expected, result.value(), "value drift at iteration " + i + " for " + code);
            if (wasmGas == null) {
                wasmGas = result.wasmGasUsed();
                hostGas = result.hostGasUsed();
            } else {
                assertEquals(wasmGas.longValue(), result.wasmGasUsed(), "wasm gas drift for " + code);
                assertEquals(hostGas.longValue(), result.hostGasUsed(), "host gas drift for " + code);
            }
        }
    }

    private static Path blueQuickJsRoot() {
        return ChicoryTestSupport.blueQuickJsRoot("blue-quickjs checkout is required for Chicory smoke tests");
    }
}
