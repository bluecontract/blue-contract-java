package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaPackagingSmokeTest {

    @Test
    void classpathPinnedResourcesEvaluateWithoutFilesystemRoot() {
        ChicoryBlueQuickJsRuntime runtime = new ChicoryBlueQuickJsRuntime(BlueQuickJsWasmRuntimeConfig.builder()
                .blueQuickJsRoot(null)
                .preferClasspathResources(true)
                .expectedEngineBuildHash("1d4584fc0552a24ee840afa2cca9f1536d47429f467585d4d5c1a5236ba96dc9")
                .build());

        JavaScriptEvaluationResult result = runtime.evaluate(new JavaScriptEvaluationRequest(
                "1 + 2",
                JavaScriptEvaluationRequest.Mode.EXPRESSION,
                Collections.<String, Object>emptyMap(),
                QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT));

        assertEquals(3, result.value());
    }
}
