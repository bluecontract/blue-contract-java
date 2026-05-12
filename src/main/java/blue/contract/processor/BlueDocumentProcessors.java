package blue.contract.processor;

import blue.language.Blue;
import blue.language.processor.DocumentProcessor;

public final class BlueDocumentProcessors {
    private BlueDocumentProcessors() {
    }

    public static Blue registerWith(Blue blue) {
        if (blue == null) {
            throw new IllegalArgumentException("blue must not be null");
        }
        ConversationProcessors.registerWith(blue);
        MyOSProcessors.registerWith(blue);
        return blue;
    }

    public static DocumentProcessor.Builder configure(DocumentProcessor.Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        return MyOSProcessors.configure(ConversationProcessors.configure(builder));
    }
}
