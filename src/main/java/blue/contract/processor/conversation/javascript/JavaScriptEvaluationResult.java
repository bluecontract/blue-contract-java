package blue.contract.processor.conversation.javascript;

public final class JavaScriptEvaluationResult {
    private final Object value;
    private final long wasmGasUsed;
    private final long hostGasUsed;

    public JavaScriptEvaluationResult(Object value, long wasmGasUsed, long hostGasUsed) {
        if (wasmGasUsed < 0) {
            throw new IllegalArgumentException("wasmGasUsed must not be negative");
        }
        if (hostGasUsed < 0) {
            throw new IllegalArgumentException("hostGasUsed must not be negative");
        }
        this.value = value;
        this.wasmGasUsed = wasmGasUsed;
        this.hostGasUsed = hostGasUsed;
    }

    public Object value() {
        return value;
    }

    public long wasmGasUsed() {
        return wasmGasUsed;
    }

    public long hostGasUsed() {
        return hostGasUsed;
    }
}
