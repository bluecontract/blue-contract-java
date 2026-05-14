package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChicoryOutOfGasTest {

    @Test
    void outOfGasBoundariesMatchNodeAndDoNotDrift() {
        Path root = blueQuickJsRoot();
        ChicoryBlueQuickJsRuntime chicory = new ChicoryBlueQuickJsRuntime(ChicoryTestSupport.pinnedConfig(root));
        try (NodeQuickJsRuntime node = new NodeQuickJsRuntime(root)) {
            assertRepeatedParity(node, chicory, "hostGasLimit = 0", "1 + 1", 0L, Collections.<String, Object>emptyMap());
            assertRepeatedParity(node, chicory, "hostGasLimit = 1", "1 + 1", 1L, Collections.<String, Object>emptyMap());
            assertRepeatedParity(node, chicory, "while true", "(() => { while (true) {} })()", 1L, Collections.<String, Object>emptyMap());
            assertRepeatedParity(node, chicory,
                    "large array loop",
                    "(() => { let sum = 0; for (let i = 0; i < 100000; i++) sum += i; return sum; })()",
                    10L,
                    Collections.<String, Object>emptyMap());
            assertRepeatedParity(node, chicory,
                    "recursive function",
                    "(() => { function f(n) { return n === 0 ? 0 : f(n - 1); } return f(1000000); })()",
                    1L,
                    Collections.<String, Object>emptyMap());
            assertRepeatedParity(node, chicory,
                    "document.get loop",
                    "(() => { while (true) { document('/counter'); } })()",
                    10L,
                    bindings());
        }
    }

    private static void assertRepeatedParity(NodeQuickJsRuntime node,
                                             ChicoryBlueQuickJsRuntime chicory,
                                             String label,
                                             String code,
                                             long hostGasLimit,
                                             Map<String, Object> bindings) {
        ChicoryParityAssertions.Evaluation previous = null;
        for (int i = 0; i < 5; i++) {
            JavaScriptEvaluationRequest request = new JavaScriptEvaluationRequest(code,
                    JavaScriptEvaluationRequest.Mode.EXPRESSION,
                    bindings,
                    hostGasLimit);
            ChicoryParityAssertions.Evaluation nodeResult = ChicoryParityAssertions.evaluate(node, request);
            ChicoryParityAssertions.Evaluation chicoryResult = ChicoryParityAssertions.evaluate(chicory, request);
            org.junit.jupiter.api.Assertions.assertEquals(nodeResult, chicoryResult, label + " iteration " + i);
            if (previous != null) {
                org.junit.jupiter.api.Assertions.assertEquals(previous, chicoryResult, label + " Chicory drift at iteration " + i);
            }
            previous = chicoryResult;
        }
    }

    private static Map<String, Object> bindings() {
        Map<String, Object> document = new LinkedHashMap<String, Object>();
        Map<String, Object> counter = new LinkedHashMap<String, Object>();
        counter.put("value", 1);
        document.put("counter", counter);
        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        bindings.put("document", document);
        bindings.put("documentCanonical", document);
        bindings.put("documentMetadata", Collections.emptyMap());
        bindings.put("steps", Collections.emptyMap());
        return bindings;
    }

    private static Path blueQuickJsRoot() {
        return ChicoryTestSupport.blueQuickJsRoot("blue-quickjs checkout is required for OOG tests");
    }
}
