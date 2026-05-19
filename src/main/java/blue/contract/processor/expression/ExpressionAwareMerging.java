package blue.contract.processor.expression;

import blue.language.Blue;
import blue.language.merge.MergingProcessor;

public final class ExpressionAwareMerging {
    private ExpressionAwareMerging() {
    }

    public static void install(Blue blue) {
        if (blue == null) {
            throw new IllegalArgumentException("blue must not be null");
        }
        MergingProcessor current = blue.getMergingProcessor();
        if (current instanceof ExpressionPreservingMergingProcessor) {
            return;
        }
        blue.mergingProcessor(new ExpressionPreservingMergingProcessor(current));
    }
}
