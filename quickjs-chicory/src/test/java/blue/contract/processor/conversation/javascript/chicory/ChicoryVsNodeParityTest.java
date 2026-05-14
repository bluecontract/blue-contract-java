package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.javascript.QuickJsGas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChicoryVsNodeParityTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> COMPARED_FIELDS = Collections.unmodifiableList(
            Arrays.asList("ok", "value", "error", "wasmGasUsed", "hostGasUsed"));

    @Test
    void deterministicFixtureSetMatchesNodeOracleIncludingGas() throws IOException {
        Path root = ChicoryTestSupport.blueQuickJsRoot("blue-quickjs checkout is required for parity tests");
        List<Map<String, Object>> reportCases = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> mismatches = new ArrayList<Map<String, Object>>();

        ChicoryBlueQuickJsRuntime chicory = new ChicoryBlueQuickJsRuntime(ChicoryTestSupport.pinnedConfig(root));
        try (NodeQuickJsRuntime node = new NodeQuickJsRuntime(root)) {
            for (Fixture fixture : fixtures()) {
                ChicoryParityAssertions.Evaluation nodeResult = ChicoryParityAssertions.evaluate(node, fixture.request);
                ChicoryParityAssertions.Evaluation chicoryResult = ChicoryParityAssertions.evaluate(chicory, fixture.request);
                Map<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("caseName", fixture.name);
                entry.put("name", fixture.name);
                entry.put("mode", fixture.request.mode().name());
                entry.put("runtimeMode", fixture.request.mode().name().toLowerCase(java.util.Locale.ROOT));
                entry.put("source", summarize(fixture.request.code()));
                entry.put("sourceSha256", sha256(fixture.request.code()));
                entry.put("bindingsSummary", fixture.bindingsSummary);
                entry.put("hostGasLimit", fixture.request.hostGasLimit());
                entry.put("comparedFields", COMPARED_FIELDS);
                entry.put("node", nodeResult.toMap());
                entry.put("chicory", chicoryResult.toMap());
                String mismatchReason = mismatchReason(nodeResult, chicoryResult);
                entry.put("status", mismatchReason == null ? "passed" : "failed");
                entry.put("finalStatus", mismatchReason == null ? "passed" : "failed");
                entry.put("mismatchReason", mismatchReason);
                entry.put("pass", mismatchReason == null);
                entry.put("matches", mismatchReason == null);
                reportCases.add(entry);
                if (mismatchReason != null) {
                    mismatches.add(entry);
                }
            }
        }

        Path reportPath = writeReport(root, reportCases, mismatches);
        assertTrue(mismatches.isEmpty(), "Chicory parity mismatches written to " + reportPath);
    }

    private static List<Fixture> fixtures() {
        Map<String, Object> bindings = bindings();
        List<Fixture> fixtures = new ArrayList<Fixture>();
        fixtures.add(expression("arithmetic", "1 + 2 * 3", bindings));
        fixtures.add(expression("sequential workflow with Update Document expression", "document('/counter') + 1", bindings));
        fixtures.add(expression("string template behavior", "`counter=${document('/counter')}; request=${event.message.request}`", bindings));
        fixtures.add(expression("event reads", "event.actor.email + ':' + event.message.request", bindings));
        fixtures.add(expression("object return", "({ ok: true, count: document('/counter') })", bindings));
        fixtures.add(expression("list return", "[1, 'two', true, null, document('/counter')]", bindings));
        fixtures.add(expression("nested object return",
                "({ outer: { amount: steps.Prepare.amount, items: [document('/counter'), { actor: event.actor.email }] } })",
                bindings));
        fixtures.add(expression("document('/path')", "document('/counter')", bindings));
        fixtures.add(expression("document.canonical('/path')", "document.canonical('/counter')", bindings));
        fixtures.add(expression("documentCanonical binding", "documentCanonical.counter.value", bindings));
        fixtures.add(expression("metadata read /counter/name", "document('/counter/name')", bindings));
        fixtures.add(expression("steps binding", "steps.Prepare.amount + steps.Prepare.delta", bindings));
        fixtures.add(expression("currentContract binding", "currentContract.channel + ':' + currentContract.description", bindings));
        fixtures.add(expression("currentContractCanonical binding",
                "currentContractCanonical.description.value + ':' + currentContractCanonical.channel.value",
                bindings));
        fixtures.add(expression("forbidden global typeof process", "typeof process", bindings));
        fixtures.add(expression("forbidden Math.random", "Math.random()", bindings));
        fixtures.add(expression("disabled Function constructor", "Function('return 1')()", bindings));
        fixtures.add(expression("thrown error", "(() => { throw new Error('boom'); })()", bindings));
        fixtures.add(expression("host-call limit error", "document('/" + repeat('p', 5000) + "')", bindings));
        fixtures.add(expression("unsupported host function", "Host.v1.unknown('/counter')", bindings));
        fixtures.add(expression("malformed host call", "document(null)", bindings));
        fixtures.add(expression("Trigger Event template expression", "`Counter is ${document('/counter')}`", bindings));
        fixtures.add(expression("recursive function",
                "(() => { function f(n) { return n === 0 ? 0 : f(n - 1) + 1; } return f(25); })()",
                bindings));
        fixtures.add(expression("document-read loop",
                "(() => { let total = 0; for (let i = 0; i < 25; i++) total += document('/counter'); return total; })()",
                bindings));
        fixtures.add(block("code block returning events",
                "return { events: [{ type: 'Conversation/Chat Message', message: `Counter ${document('/counter')}` }] };",
                bindings));
        fixtures.add(block("sequential workflow with JavaScript Code",
                "return { value: document('/counter') + steps.Prepare.delta };",
                bindings));
        fixtures.add(block("code block returning non-event object",
                "return { value: document('/counter'), nested: { ok: true } };",
                bindings));
        fixtures.add(expression("null return", "null", bindings));
        fixtures.add(expression("large but valid object",
                "(() => { const value = {}; for (let i = 0; i < 64; i++) value['k' + i] = i; return value; })()",
                bindings));
        fixtures.add(expression("invalid deterministic value return", "NaN", bindings));
        fixtures.add(expression("syntax error category", "const =", bindings));
        fixtures.add(new Fixture("out-of-gas loop", new JavaScriptEvaluationRequest(
                "(() => { while (true) {} })()",
                JavaScriptEvaluationRequest.Mode.EXPRESSION,
                bindings,
                1L)));
        fixtures.add(new Fixture("out-of-gas document-read loop", new JavaScriptEvaluationRequest(
                "(() => { while (true) { document('/counter'); } })()",
                JavaScriptEvaluationRequest.Mode.EXPRESSION,
                bindings,
                10L)));
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

    private static String mismatchReason(ChicoryParityAssertions.Evaluation nodeResult,
                                         ChicoryParityAssertions.Evaluation chicoryResult) {
        Map<String, Object> node = nodeResult.toMap();
        Map<String, Object> chicory = chicoryResult.toMap();
        for (String field : COMPARED_FIELDS) {
            if (!java.util.Objects.equals(node.get(field), chicory.get(field))) {
                return field + " mismatch";
            }
        }
        return null;
    }

    private static Path writeReport(Path root,
                                    List<Map<String, Object>> cases,
                                    List<Map<String, Object>> mismatches) throws IOException {
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("status", mismatches.isEmpty() ? "passed" : "failed");
        report.put("caseCount", cases.size());
        report.put("generatedTimestamp", Instant.now().toString());
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("engineBuildHash", ChicoryTestSupport.engineBuildHash(root));
        report.put("gasVersion", ChicoryTestSupport.gasVersion(root));
        report.put("executionProfile", BlueQuickJsWasmRuntimeConfig.DEFAULT_EXECUTION_PROFILE);
        report.put("hostV1Hash", HostV1Manifest.HOST_V1_HASH);
        report.put("comparedFields", COMPARED_FIELDS);
        report.put("cases", cases);
        report.put("mismatches", mismatches);
        Path reportPath = ChicoryTestSupport.reportPath("blue-quickjs-chicory-parity.json");
        Files.createDirectories(reportPath.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        return reportPath;
    }

    private static String summarize(String source) {
        if (source == null) {
            return "";
        }
        String compact = source.replaceAll("\\s+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 237) + "...";
    }

    private static String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((source == null ? "" : source).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError("SHA-256 is required", ex);
        }
    }

    private static Map<String, Object> bindings() {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("actor", singleton("email", "alice@example.com"));
        event.put("message", singleton("request", 7));

        Map<String, Object> eventCanonical = new LinkedHashMap<String, Object>();
        Map<String, Object> canonicalMessage = new LinkedHashMap<String, Object>();
        canonicalMessage.put("request", singleton("value", 7));
        eventCanonical.put("message", canonicalMessage);

        Map<String, Object> prepare = new LinkedHashMap<String, Object>();
        prepare.put("amount", 5);
        prepare.put("delta", 2);
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
        document.put("items", Arrays.asList(singleton("value", 1), singleton("value", 2)));

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

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static final class Fixture {
        private final String name;
        private final JavaScriptEvaluationRequest request;
        private final String bindingsSummary;

        private Fixture(String name, JavaScriptEvaluationRequest request) {
            this.name = name;
            this.request = request;
            this.bindingsSummary = "keys=" + request.bindings().keySet()
                    + ", codeChars=" + request.code().length();
        }
    }
}
