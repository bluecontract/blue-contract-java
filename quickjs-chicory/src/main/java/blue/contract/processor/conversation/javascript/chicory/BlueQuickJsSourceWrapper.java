package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;

public final class BlueQuickJsSourceWrapper {
    private static final String PRELUDE = "\n"
            + "const __blueDocument = globalThis.document;\n"
            + "const document = Object.assign(\n"
            + "  (pointer = '/') => __blueDocument(pointer),\n"
            + "  { canonical: (pointer = '/') => __blueDocument.canonical(pointer) },\n"
            + ");\n";

    private BlueQuickJsSourceWrapper() {
    }

    public static String wrap(JavaScriptEvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return wrap(request.code(), request.mode());
    }

    public static String wrap(String code, JavaScriptEvaluationRequest.Mode mode) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (mode == JavaScriptEvaluationRequest.Mode.EXPRESSION) {
            return "(() => {\n" + PRELUDE + "\nreturn (" + code + ");\n})()";
        }
        if (mode == JavaScriptEvaluationRequest.Mode.BLOCK) {
            return "(() => {\n" + PRELUDE + "\n" + code + "\n})()";
        }
        return code;
    }

    static String raw(String code) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        return code;
    }
}
