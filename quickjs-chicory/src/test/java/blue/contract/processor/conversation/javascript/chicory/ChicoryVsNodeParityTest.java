package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptExecutionException;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChicoryVsNodeParityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void deterministicFixtureSetMatchesNodeOracle() throws IOException {
        Path root = blueQuickJsRoot();
        List<Map<String, Object>> reportCases = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> mismatches = new ArrayList<Map<String, Object>>();

        ChicoryBlueQuickJsRuntime chicory = new ChicoryBlueQuickJsRuntime(BlueQuickJsWasmRuntimeConfig.builder()
                .blueQuickJsRoot(root)
                .expectedEngineBuildHash("1d4584fc0552a24ee840afa2cca9f1536d47429f467585d4d5c1a5236ba96dc9")
                .build());
        try (NodeQuickJsRuntime node = new NodeQuickJsRuntime(root)) {
            for (Fixture fixture : fixtures()) {
                Evaluation nodeResult = evaluate(node, fixture.request);
                Evaluation chicoryResult = evaluate(chicory, fixture.request);
                Map<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("name", fixture.name);
                entry.put("node", nodeResult.toMap());
                entry.put("chicory", chicoryResult.toMap());
                entry.put("matches", nodeResult.equals(chicoryResult));
                reportCases.add(entry);
                if (!nodeResult.equals(chicoryResult)) {
                    mismatches.add(entry);
                }
            }
        }

        writeReport(reportCases, mismatches);
        assertTrue(mismatches.isEmpty(), "Chicory parity mismatches written to build report");
    }

    private static List<Fixture> fixtures() {
        Map<String, Object> bindings = bindings();
        List<Fixture> fixtures = new ArrayList<Fixture>();
        fixtures.add(expression("simple arithmetic expression", "1 + 2", bindings));
        fixtures.add(expression("event binding expression", "event.message.request + 1", bindings));
        fixtures.add(expression("currentContract expression", "currentContract.channel", bindings));
        fixtures.add(expression("steps binding expression", "steps.Prepare.amount + 1", bindings));
        fixtures.add(expression("document simple value", "document('/counter')", bindings));
        fixtures.add(expression("document canonical value", "document.canonical('/counter').value", bindings));
        fixtures.add(expression("document metadata lookup", "document('/counter/name')", bindings));
        fixtures.add(expression("array map reduce", "[1, 2, 3].map(x => x + 1).reduce((a, b) => a + b, 0)", bindings));
        fixtures.add(expression("JSON stringify deterministic case", "JSON.stringify({ b: 2, aa: 1 })", bindings));
        fixtures.add(block("block mode returning object", "return { value: document('/counter') + event.message.request };", bindings));
        fixtures.add(block("block mode returning array", "return [event.message.request, document('/counter')];", bindings));
        fixtures.add(expression("forbidden global", "typeof Date", bindings));
        fixtures.add(new Fixture("out-of-gas loop", new JavaScriptEvaluationRequest(
                "(() => { while (true) {} })()",
                JavaScriptEvaluationRequest.Mode.EXPRESSION,
                bindings,
                1L)));
        return fixtures;
    }

    private static Fixture expression(String name, String code, Map<String, Object> bindings) {
        return new Fixture(name, new JavaScriptEvaluationRequest(code,
                JavaScriptEvaluationRequest.Mode.EXPRESSION,
                bindings,
                QuickJsGas.DEFAULT_EXPRESSION_HOST_GAS_LIMIT));
    }

    private static Fixture block(String name, String code, Map<String, Object> bindings) {
        return new Fixture(name, new JavaScriptEvaluationRequest(code,
                JavaScriptEvaluationRequest.Mode.BLOCK,
                bindings,
                QuickJsGas.DEFAULT_CODE_HOST_GAS_LIMIT));
    }

    private static Evaluation evaluate(blue.contract.processor.conversation.javascript.JavaScriptRuntime runtime,
                                       JavaScriptEvaluationRequest request) {
        try {
            JavaScriptEvaluationResult result = runtime.evaluate(request);
            return Evaluation.ok(result.value(), result.wasmGasUsed(), result.hostGasUsed());
        } catch (JavaScriptExecutionException ex) {
            return Evaluation.error(normalizeMessage(ex.getMessage()));
        }
    }

    private static String normalizeMessage(String message) {
        return message == null ? "" : message.replace("Chicory blue-quickjs evaluation failed: ", "");
    }

    private static void writeReport(List<Map<String, Object>> cases,
                                    List<Map<String, Object>> mismatches) throws IOException {
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("status", mismatches.isEmpty() ? "passed" : "failed");
        report.put("cases", cases);
        report.put("mismatches", mismatches);
        Path reportPath = Paths.get(System.getProperty("user.dir"),
                "build/reports/blue-quickjs-chicory-parity.json");
        Files.createDirectories(reportPath.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
    }

    private static Map<String, Object> bindings() {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("message", singleton("request", 7));

        Map<String, Object> eventCanonical = new LinkedHashMap<String, Object>();
        Map<String, Object> canonicalMessage = new LinkedHashMap<String, Object>();
        canonicalMessage.put("request", singleton("value", 7));
        eventCanonical.put("message", canonicalMessage);

        Map<String, Object> prepare = new LinkedHashMap<String, Object>();
        prepare.put("amount", 5);
        Map<String, Object> steps = new LinkedHashMap<String, Object>();
        steps.put("Prepare", prepare);

        Map<String, Object> currentContract = new LinkedHashMap<String, Object>();
        currentContract.put("channel", "ownerChannel");
        currentContract.put("description", "Demo workflow");

        Map<String, Object> currentContractCanonical = new LinkedHashMap<String, Object>();
        currentContractCanonical.put("channel", singleton("value", "ownerChannel"));
        currentContractCanonical.put("description", singleton("value", "Demo workflow"));

        Map<String, Object> counter = new LinkedHashMap<String, Object>();
        counter.put("name", "Canonical counter name");
        counter.put("type", singleton("value", "Integer"));
        counter.put("value", 6);

        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("name", "Counter");
        document.put("counter", counter);

        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("/counter/name", "Counter label");

        Map<String, Object> bindings = new LinkedHashMap<String, Object>();
        bindings.put("event", event);
        bindings.put("eventCanonical", eventCanonical);
        bindings.put("steps", steps);
        bindings.put("currentContract", currentContract);
        bindings.put("currentContractCanonical", currentContractCanonical);
        bindings.put("document", document);
        bindings.put("documentCanonical", document);
        bindings.put("documentMetadata", metadata);
        return bindings;
    }

    private static Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    private static Path blueQuickJsRoot() {
        String configured = System.getProperty("blue.quickjs.root");
        Path root = configured == null || configured.trim().isEmpty()
                ? Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent().resolve("blue-quickjs")
                : Paths.get(configured);
        assumeTrue(Files.isDirectory(root), "blue-quickjs checkout is required for parity tests");
        return root;
    }

    private static final class Fixture {
        private final String name;
        private final JavaScriptEvaluationRequest request;

        private Fixture(String name, JavaScriptEvaluationRequest request) {
            this.name = name;
            this.request = request;
        }
    }

    private static final class Evaluation {
        private final boolean ok;
        private final Object value;
        private final String error;
        private final long wasmGasUsed;
        private final long hostGasUsed;

        private Evaluation(boolean ok, Object value, String error, long wasmGasUsed, long hostGasUsed) {
            this.ok = ok;
            this.value = value;
            this.error = error;
            this.wasmGasUsed = wasmGasUsed;
            this.hostGasUsed = hostGasUsed;
        }

        private static Evaluation ok(Object value, long wasmGasUsed, long hostGasUsed) {
            return new Evaluation(true, normalize(value), null, wasmGasUsed, hostGasUsed);
        }

        private static Evaluation error(String error) {
            return new Evaluation(false, null, error, -1L, -1L);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("ok", ok);
            map.put("value", value);
            map.put("error", error);
            map.put("wasmGasUsed", wasmGasUsed);
            map.put("hostGasUsed", hostGasUsed);
            return map;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Evaluation)) {
                return false;
            }
            Evaluation that = (Evaluation) other;
            return ok == that.ok
                    && wasmGasUsed == that.wasmGasUsed
                    && hostGasUsed == that.hostGasUsed
                    && java.util.Objects.equals(value, that.value)
                    && java.util.Objects.equals(error, that.error);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(ok, value, error, wasmGasUsed, hostGasUsed);
        }

        @SuppressWarnings("unchecked")
        private static Object normalize(Object value) {
            if (value instanceof Number) {
                Number number = (Number) value;
                if (number.doubleValue() == Math.rint(number.doubleValue())
                        && number.longValue() >= Integer.MIN_VALUE
                        && number.longValue() <= Integer.MAX_VALUE) {
                    return Integer.valueOf(number.intValue());
                }
                return value;
            }
            if (value instanceof List) {
                List<Object> result = new ArrayList<Object>();
                for (Object item : (List<Object>) value) {
                    result.add(normalize(item));
                }
                return result;
            }
            if (value instanceof Map) {
                Map<String, Object> result = new LinkedHashMap<String, Object>();
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                    result.put(entry.getKey(), normalize(entry.getValue()));
                }
                return result;
            }
            return value;
        }
    }
}
