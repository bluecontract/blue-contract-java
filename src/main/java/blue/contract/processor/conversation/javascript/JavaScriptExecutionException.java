package blue.contract.processor.conversation.javascript;

public class JavaScriptExecutionException extends RuntimeException {
    public JavaScriptExecutionException(String message) {
        super(message);
    }

    public JavaScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
