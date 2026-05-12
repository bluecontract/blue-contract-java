package blue.contract.processor.conversation.javascript.chicory;

import blue.contract.processor.conversation.javascript.JavaScriptEvaluationRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueQuickJsSourceWrapperTest {
    private static final String PRELUDE = "\n"
            + "const __blueDocument = globalThis.document;\n"
            + "const document = Object.assign(\n"
            + "  (pointer = '/') => __blueDocument(pointer),\n"
            + "  { canonical: (pointer = '/') => __blueDocument.canonical(pointer) },\n"
            + ");\n";

    @Test
    void expressionWrapperMatchesEvaluateMjs() {
        assertEquals("(() => {\n" + PRELUDE + "\nreturn (counter + 1);\n})()",
                BlueQuickJsSourceWrapper.wrap("counter + 1", JavaScriptEvaluationRequest.Mode.EXPRESSION));
    }

    @Test
    void blockWrapperMatchesEvaluateMjs() {
        assertEquals("(() => {\n" + PRELUDE + "\nconst x = 1; return x;\n})()",
                BlueQuickJsSourceWrapper.wrap("const x = 1; return x;", JavaScriptEvaluationRequest.Mode.BLOCK));
    }

    @Test
    void rawWrapperLeavesCodeUntouched() {
        assertEquals("1 + 1", BlueQuickJsSourceWrapper.raw("1 + 1"));
    }
}
