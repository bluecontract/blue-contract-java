package blue.contract.processor;

import blue.contract.processor.myos.MyOSTimelineChannelProcessor;
import blue.language.Blue;
import blue.language.processor.DocumentProcessor;
import blue.language.utils.TypeClassResolver;
import blue.repo.v1_2_0.BlueRepositoryV1_2_0;

public final class MyOSProcessors {
    private MyOSProcessors() {
    }

    public static Blue registerWith(Blue blue) {
        if (blue == null) {
            throw new IllegalArgumentException("blue must not be null");
        }
        BlueRepositoryV1_2_0.registerAll(blue.getDocumentProcessor().getContractTypeResolver());
        blue.registerContractProcessor(new MyOSTimelineChannelProcessor());
        return blue;
    }

    public static DocumentProcessor.Builder configure(DocumentProcessor.Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        TypeClassResolver resolver = BlueRepositoryV1_2_0.registerAll(
                new TypeClassResolver("blue.language.processor.model"));
        return builder
                .withContractTypeResolver(resolver)
                .registerContractProcessor(new MyOSTimelineChannelProcessor());
    }
}
