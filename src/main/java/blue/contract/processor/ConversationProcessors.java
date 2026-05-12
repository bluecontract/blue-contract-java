package blue.contract.processor;

import blue.contract.processor.conversation.CompositeTimelineChannelProcessor;
import blue.contract.processor.conversation.OperationProcessor;
import blue.contract.processor.conversation.SequentialWorkflowOperationProcessor;
import blue.contract.processor.conversation.SequentialWorkflowProcessor;
import blue.language.Blue;
import blue.language.processor.DocumentProcessor;
import blue.language.utils.TypeClassResolver;
import blue.repo.v1_2_0.BlueRepositoryV1_2_0;

public final class ConversationProcessors {
    private ConversationProcessors() {
    }

    public static Blue registerWith(Blue blue) {
        if (blue == null) {
            throw new IllegalArgumentException("blue must not be null");
        }
        BlueRepositoryV1_2_0.registerAll(blue.getDocumentProcessor().getContractTypeResolver());
        blue.registerContractProcessor(new CompositeTimelineChannelProcessor());
        blue.registerContractProcessor(new OperationProcessor());
        blue.registerContractProcessor(new SequentialWorkflowProcessor());
        blue.registerContractProcessor(new SequentialWorkflowOperationProcessor());
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
                .registerContractProcessor(new CompositeTimelineChannelProcessor())
                .registerContractProcessor(new OperationProcessor())
                .registerContractProcessor(new SequentialWorkflowProcessor())
                .registerContractProcessor(new SequentialWorkflowOperationProcessor());
    }
}
