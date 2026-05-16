package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChicoryBenchmarkReportTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @Tag("benchmark")
    void writesNodeAndChicoryBenchmarkReportWithoutTimingAssertions() throws IOException {
        Path root = ChicoryTestSupport.blueQuickJsRoot("blue-quickjs checkout is required for benchmark report tests");
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

        ChicoryBlueQuickJsRuntime chicory = new ChicoryBlueQuickJsRuntime(ChicoryTestSupport.pinnedConfig(root));
        try (NodeQuickJsRuntime node = new NodeQuickJsRuntime(root)) {
            BenchmarkCase hundred = benchmarkCase("100 arithmetic iterations", 100,
                    expression("1 + 2", Collections.<String, Object>emptyMap()));
            BenchmarkCase simple = benchmarkCase("1000 simple expressions", 1000,
                    expression("1 + 2", Collections.<String, Object>emptyMap()));
            BenchmarkCase documentRead = benchmarkCase("100 document-read expressions", 100,
                    expression("document('/counter')", documentBindings()));

            for (BenchmarkCase benchmark : new BenchmarkCase[]{hundred, simple, documentRead}) {
                BenchmarkResult nodeResult = run("Node bridge", node, benchmark);
                BenchmarkResult chicoryResult = run("Chicory", chicory, benchmark);
                assertEquals(nodeResult.minGas, chicoryResult.minGas, benchmark.name + " minGas");
                assertEquals(nodeResult.maxGas, chicoryResult.maxGas, benchmark.name + " maxGas");
                assertEquals(nodeResult.totalGas, chicoryResult.totalGas, benchmark.name + " totalGas");
                results.add(nodeResult.toMap());
                results.add(chicoryResult.toMap());
            }
        }

        Path reportPath = ChicoryTestSupport.reportPath("blue-quickjs-chicory-benchmarks.json");
        Files.createDirectories(reportPath.getParent());
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("engineBuildHash", ChicoryTestSupport.engineBuildHash(root));
        report.put("gasVersion", ChicoryTestSupport.gasVersion(root));
        report.put("results", results);
        JSON.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
    }

    private static JavaScriptEvaluationRequest expression(String code, Map<String, Object> bindings) {
        return new JavaScriptEvaluationRequest(code,
                JavaScriptEvaluationRequest.Mode.EXPRESSION,
                bindings,
                QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT);
    }

    private static BenchmarkCase benchmarkCase(String name,
                                               int iterations,
                                               JavaScriptEvaluationRequest request) {
        return new BenchmarkCase(name, iterations, request);
    }

    private static BenchmarkResult run(String runtime,
                                       JavaScriptRuntime evaluator,
                                       BenchmarkCase benchmark) {
        long started = System.nanoTime();
        long minGas = Long.MAX_VALUE;
        long maxGas = 0L;
        long totalGas = 0L;
        for (int i = 0; i < benchmark.iterations; i++) {
            JavaScriptEvaluationResult result = evaluator.evaluate(benchmark.request);
            minGas = Math.min(minGas, result.wasmGasUsed());
            maxGas = Math.max(maxGas, result.wasmGasUsed());
            totalGas += result.wasmGasUsed();
        }
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        return new BenchmarkResult(runtime,
                benchmark.name,
                benchmark.iterations,
                elapsedMillis,
                minGas,
                maxGas,
                totalGas);
    }

    private static Map<String, Object> documentBindings() {
        Map<String, Object> counter = new LinkedHashMap<String, Object>();
        counter.put("value", 6);
        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("counter", counter);
        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        bindings.put("document", document);
        bindings.put("documentCanonical", document);
        bindings.put("documentMetadata", Collections.emptyMap());
        return bindings;
    }

    private static final class BenchmarkCase {
        private final String name;
        private final int iterations;
        private final JavaScriptEvaluationRequest request;

        private BenchmarkCase(String name, int iterations, JavaScriptEvaluationRequest request) {
            this.name = name;
            this.iterations = iterations;
            this.request = request;
        }
    }

    private static final class BenchmarkResult {
        private final String runtime;
        private final String scenario;
        private final int iterations;
        private final long elapsedMillis;
        private final long minGas;
        private final long maxGas;
        private final long totalGas;

        private BenchmarkResult(String runtime,
                                String scenario,
                                int iterations,
                                long elapsedMillis,
                                long minGas,
                                long maxGas,
                                long totalGas) {
            this.runtime = runtime;
            this.scenario = scenario;
            this.iterations = iterations;
            this.elapsedMillis = elapsedMillis;
            this.minGas = minGas;
            this.maxGas = maxGas;
            this.totalGas = totalGas;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("runtime", runtime);
            map.put("scenario", scenario);
            map.put("iterations", iterations);
            map.put("elapsedMillis", elapsedMillis);
            map.put("minGas", minGas);
            map.put("maxGas", maxGas);
            map.put("totalGas", totalGas);
            map.put("finalBlueId", null);
            return map;
        }
    }
}
