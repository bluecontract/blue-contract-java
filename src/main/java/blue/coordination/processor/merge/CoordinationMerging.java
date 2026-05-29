package blue.coordination.processor.merge;

import blue.language.Blue;
import blue.language.merge.MergingProcessor;

public final class CoordinationMerging {
    private CoordinationMerging() {
    }

    public static void install(Blue blue) {
        if (blue == null) {
            throw new IllegalArgumentException("blue must not be null");
        }
        MergingProcessor current = blue.getMergingProcessor();
        if (current instanceof ComputeRuntimeDefaultMergingProcessor) {
            return;
        }
        blue.mergingProcessor(new ComputeRuntimeDefaultMergingProcessor(current));
    }
}
