package blue.contract.processor.conversation.javascript;

public class JavaScriptExecutionException extends RuntimeException {
    private final Long wasmGasUsed;
    private final Long hostGasUsed;

    public JavaScriptExecutionException(String message) {
        this(message, null, null, null);
    }

    public JavaScriptExecutionException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    public JavaScriptExecutionException(String message, long wasmGasUsed, long hostGasUsed) {
        this(message, null, wasmGasUsed, hostGasUsed);
    }

    public JavaScriptExecutionException(String message, Throwable cause, Long wasmGasUsed, Long hostGasUsed) {
        super(message, cause);
        this.wasmGasUsed = wasmGasUsed;
        this.hostGasUsed = hostGasUsed;
    }

    public boolean hasGasUsage() {
        return wasmGasUsed != null && hostGasUsed != null;
    }

    public long wasmGasUsed() {
        return wasmGasUsed != null ? wasmGasUsed.longValue() : -1L;
    }

    public long hostGasUsed() {
        return hostGasUsed != null ? hostGasUsed.longValue() : -1L;
    }
}
