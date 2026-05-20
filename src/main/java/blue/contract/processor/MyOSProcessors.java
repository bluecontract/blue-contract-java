package blue.contract.processor;

import blue.contract.processor.myos.MyOSTimelineChannelProcessor;
import blue.language.Blue;
import blue.language.processor.DocumentProcessor;
import blue.language.utils.TypeClassResolver;
import blue.repo.BlueRepositoryModels;

public final class MyOSProcessors {
    private MyOSProcessors() {
    }

    public static Blue registerWith(Blue blue) {
        if (blue == null) {
            throw new IllegalArgumentException("blue must not be null");
        }
        BlueRepositoryModels.registerAll(blue.getDocumentProcessor().getContractTypeResolver());
        blue.registerContractProcessor(new MyOSTimelineChannelProcessor());
        return blue;
    }

    public static DocumentProcessor.Builder configure(DocumentProcessor.Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        TypeClassResolver resolver = BlueRepositoryModels.registerAll(
                new TypeClassResolver("blue.language.processor.model"));
        return builder
                .withContractTypeResolver(resolver)
                .registerContractProcessor(new MyOSTimelineChannelProcessor());
    }
}
