package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import blue.contract.processor.conversation.javascript.JavaScriptEvaluationResult;
import blue.contract.processor.conversation.javascript.JavaScriptExecutionException;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChicoryParityAssertions {
    private ChicoryParityAssertions() {
    }

    static Evaluation evaluate(JavaScriptRuntime runtime, JavaScriptEvaluationRequest request) {
        try {
            JavaScriptEvaluationResult result = runtime.evaluate(request);
            return Evaluation.ok(result.value(), result.wasmGasUsed(), result.hostGasUsed());
        } catch (JavaScriptExecutionException ex) {
            return Evaluation.error(normalizeMessage(ex.getMessage()));
        }
    }

    static void assertParity(String label,
                             JavaScriptRuntime node,
                             JavaScriptRuntime chicory,
                             JavaScriptEvaluationRequest request) {
        Evaluation expected = evaluate(node, request);
        Evaluation actual = evaluate(chicory, request);
        assertEquals(expected, actual, label + " should match Node oracle");
    }

    private static String normalizeMessage(String message) {
        return message == null ? "" : message.replace("Chicory blue-quickjs evaluation failed: ", "");
    }

    static final class Evaluation {
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

        static Evaluation ok(Object value, long wasmGasUsed, long hostGasUsed) {
            return new Evaluation(true, normalize(value), null, wasmGasUsed, hostGasUsed);
        }

        static Evaluation error(String error) {
            return new Evaluation(false, null, error, -1L, -1L);
        }

        Map<String, Object> toMap() {
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

        @Override
        public String toString() {
            return toMap().toString();
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
