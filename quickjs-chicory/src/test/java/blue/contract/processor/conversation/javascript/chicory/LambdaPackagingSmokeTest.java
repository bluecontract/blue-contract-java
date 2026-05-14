package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaPackagingSmokeTest {

    @Test
    void classpathPinnedResourcesEvaluateWithoutFilesystemRoot() {
        ChicoryBlueQuickJsRuntime runtime = new ChicoryBlueQuickJsRuntime(BlueQuickJsWasmRuntimeConfig.builder()
                .blueQuickJsRoot(null)
                .preferClasspathResources(true)
                .build());

        JavaScriptEvaluationResult result = runtime.evaluate(new JavaScriptEvaluationRequest(
                "1 + 2",
                JavaScriptEvaluationRequest.Mode.EXPRESSION,
                Collections.<String, Object>emptyMap(),
                QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT));

        assertEquals(3, result.value());
    }

    @Test
    void runtimeClasspathDoesNotContainNativeJavaScriptEngines() {
        String classpath = System.getProperty("java.class.path").toLowerCase();
        for (String forbidden : new String[]{"javet", "wasmtime", "quickjs4j", "graal-js", "org.graalvm.js", "jna"}) {
            assertFalse(classpath.contains(forbidden), "unexpected runtime dependency on " + forbidden);
        }
    }

    @Test
    void classpathRuntimeWorksWhenNodeIsNotOnPath() throws IOException, InterruptedException {
        String java = System.getProperty("java.home") + "/bin/java";
        ProcessBuilder builder = new ProcessBuilder(java,
                "-cp",
                System.getProperty("java.class.path"),
                NoNodeClasspathSmokeMain.class.getName());
        builder.environment().put("PATH", "/bin");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = readOutput(process);
        int exit = process.waitFor();

        assertEquals(0, exit, output);
        assertTrue(output.contains("nodeUnavailable=true"), output);
        assertTrue(output.contains("value=3"), output);
    }

    private static String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    public static final class NoNodeClasspathSmokeMain {
        public static void main(String[] args) {
            boolean nodeUnavailable = nodeUnavailable();
            if (!nodeUnavailable) {
                System.out.println("nodeUnavailable=false");
                System.exit(2);
            }
            ChicoryBlueQuickJsRuntime runtime = new ChicoryBlueQuickJsRuntime(BlueQuickJsWasmRuntimeConfig.builder()
                    .blueQuickJsRoot(null)
                    .preferClasspathResources(true)
                    .build());
            JavaScriptEvaluationResult result = runtime.evaluate(new JavaScriptEvaluationRequest(
                    "1 + 2",
                    JavaScriptEvaluationRequest.Mode.EXPRESSION,
                    Collections.<String, Object>emptyMap(),
                    QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT));
            System.out.println("nodeUnavailable=true");
            System.out.println("value=" + result.value());
        }

        private static boolean nodeUnavailable() {
            try {
                Process process = new ProcessBuilder("node", "--version").start();
                process.destroyForcibly();
                return false;
            } catch (IOException ex) {
                return true;
            }
        }
    }
}
