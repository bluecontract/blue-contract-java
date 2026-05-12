package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChicoryForbiddenSurfaceTest {

    @Test
    void forbiddenSurfaceMatchesNodeOracle() {
        Path root = blueQuickJsRoot();
        ChicoryBlueQuickJsRuntime chicory = new ChicoryBlueQuickJsRuntime(BlueQuickJsWasmRuntimeConfig.builder()
                .blueQuickJsRoot(root)
                .expectedEngineBuildHash("1d4584fc0552a24ee840afa2cca9f1536d47429f467585d4d5c1a5236ba96dc9")
                .build());
        try (NodeQuickJsRuntime node = new NodeQuickJsRuntime(root)) {
            assertParity(node, chicory, "typeof Date");
            assertParity(node, chicory, "typeof process");
            assertParity(node, chicory, "typeof require");
            assertParity(node, chicory, "Math.random()");
            assertParity(node, chicory, "eval(\"1\")");
            assertParity(node, chicory, "Function(\"return 1\")()");
            assertParity(node, chicory, "new Proxy({}, {})");
            assertParity(node, chicory, "typeof WeakRef");
        }
    }

    private static void assertParity(NodeQuickJsRuntime node, ChicoryBlueQuickJsRuntime chicory, String code) {
        ChicoryParityAssertions.assertParity(code,
                node,
                chicory,
                new JavaScriptEvaluationRequest(code,
                        JavaScriptEvaluationRequest.Mode.EXPRESSION,
                        Collections.<String, Object>emptyMap(),
                        QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT));
    }

    private static Path blueQuickJsRoot() {
        String configured = System.getProperty("blue.quickjs.root");
        Path root = configured == null || configured.trim().isEmpty()
                ? Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent().resolve("blue-quickjs")
                : Paths.get(configured);
        assumeTrue(Files.isDirectory(root), "blue-quickjs checkout is required for forbidden surface tests");
        return root;
    }
}
