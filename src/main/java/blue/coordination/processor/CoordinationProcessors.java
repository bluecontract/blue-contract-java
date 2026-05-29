package blue.coordination.processor;

import blue.bex.api.BexEngine;
import blue.coordination.processor.merge.CoordinationMerging;
import blue.coordination.processor.workflow.SequentialWorkflowRunner;
import blue.language.Blue;
import blue.language.processor.DocumentProcessor;
import blue.language.utils.TypeClassResolver;
import blue.repo.BlueRepositoryModels;

public final class CoordinationProcessors {
    private CoordinationProcessors() {
    }

    public static Blue registerWith(Blue blue) {
        return registerWith(blue, null);
    }

    public static Blue registerWith(Blue blue, CoordinationProcessorOptions options) {
        if (blue == null) {
            throw new IllegalArgumentException("blue must not be null");
        }
        SequentialWorkflowRunner runner = workflowRunner(options);
        BlueRepositoryModels.registerAll(blue.getDocumentProcessor().getContractTypeResolver());
        blue.registerContractProcessor(new CompositeTimelineChannelProcessor());
        blue.registerContractProcessor(new OperationProcessor());
        blue.registerContractProcessor(runner != null
                ? new SequentialWorkflowProcessor(runner)
                : new SequentialWorkflowProcessor());
        blue.registerContractProcessor(runner != null
                ? new SequentialWorkflowOperationProcessor(runner)
                : new SequentialWorkflowOperationProcessor());
        CoordinationMerging.install(blue);
        return blue;
    }

    public static DocumentProcessor.Builder configure(DocumentProcessor.Builder builder) {
        return configure(builder, null);
    }

    public static DocumentProcessor.Builder configure(DocumentProcessor.Builder builder,
                                                      CoordinationProcessorOptions options) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        SequentialWorkflowRunner runner = workflowRunner(options);
        TypeClassResolver resolver = BlueRepositoryModels.registerAll(
                new TypeClassResolver("blue.language.processor.model"));
        return builder
                .withContractTypeResolver(resolver)
                .registerContractProcessor(new CompositeTimelineChannelProcessor())
                .registerContractProcessor(new OperationProcessor())
                .registerContractProcessor(runner != null
                        ? new SequentialWorkflowProcessor(runner)
                        : new SequentialWorkflowProcessor())
                .registerContractProcessor(runner != null
                        ? new SequentialWorkflowOperationProcessor(runner)
                        : new SequentialWorkflowOperationProcessor());
    }

    private static SequentialWorkflowRunner workflowRunner(CoordinationProcessorOptions options) {
        if (options == null) {
            return null;
        }
        if (options.sequentialWorkflowRunner() != null) {
            return options.sequentialWorkflowRunner();
        }
        BexEngine bexEngine = options.bexEngine() != null
                ? options.bexEngine()
                : BexEngine.builder().build();
        return SequentialWorkflowRunner.withBexEngine(bexEngine,
                options.defaultComputeGasLimit(),
                options.processingMetrics());
    }
}
