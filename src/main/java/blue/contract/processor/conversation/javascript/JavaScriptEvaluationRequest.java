package blue.contract.processor.conversation.javascript;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JavaScriptEvaluationRequest {
    public enum Mode {
        EXPRESSION,
        BLOCK
    }

    private final String code;
    private final Mode mode;
    private final Map<String, Object> bindings;
    private final long hostGasLimit;

    public JavaScriptEvaluationRequest(String code,
                                       Mode mode,
                                       Map<String, Object> bindings,
                                       long hostGasLimit) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (hostGasLimit < 0) {
            throw new IllegalArgumentException("hostGasLimit must not be negative");
        }
        this.code = code;
        this.mode = mode;
        this.bindings = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                bindings != null ? bindings : Collections.<String, Object>emptyMap()));
        this.hostGasLimit = hostGasLimit;
    }

    public String code() {
        return code;
    }

    public Mode mode() {
        return mode;
    }

    public Map<String, Object> bindings() {
        return bindings;
    }

    public long hostGasLimit() {
        return hostGasLimit;
    }
}
