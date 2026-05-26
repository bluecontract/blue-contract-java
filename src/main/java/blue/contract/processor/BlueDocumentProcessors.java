package blue.contract.processor;

import blue.language.Blue;
import blue.language.processor.DocumentProcessor;

public final class BlueDocumentProcessors {
    private BlueDocumentProcessors() {
    }

    public static Blue registerWith(Blue blue) {
        return registerWith(blue, null);
    }

    public static Blue registerWith(Blue blue, BlueDocumentProcessorOptions options) {
        if (blue == null) {
            throw new IllegalArgumentException("blue must not be null");
        }
        if (options != null && options.processingMetrics() != null) {
            blue.getDocumentProcessor().processingMetricsSink(options.processingMetrics());
        }
        ConversationProcessors.registerWith(blue, options);
        MyOSProcessors.registerWith(blue);
        return blue;
    }

    public static DocumentProcessor.Builder configure(DocumentProcessor.Builder builder) {
        return configure(builder, null);
    }

    public static DocumentProcessor.Builder configure(DocumentProcessor.Builder builder,
                                                      BlueDocumentProcessorOptions options) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        if (options != null && options.processingMetrics() != null) {
            builder.withProcessingMetricsSink(options.processingMetrics());
        }
        return MyOSProcessors.configure(ConversationProcessors.configure(builder, options));
    }
}
