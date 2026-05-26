package blue.contract.processor;

import blue.bex.api.BexEngine;
import blue.contract.processor.conversation.CompositeTimelineChannelProcessor;
import blue.contract.processor.conversation.OperationProcessor;
import blue.contract.processor.conversation.SequentialWorkflowOperationProcessor;
import blue.contract.processor.conversation.SequentialWorkflowProcessor;
import blue.contract.processor.conversation.javascript.JavaScriptRuntime;
import blue.contract.processor.conversation.javascript.NodeQuickJsRuntime;
import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;
import blue.contract.processor.expression.ExpressionAwareMerging;
import blue.language.Blue;
import blue.language.processor.DocumentProcessor;
import blue.language.utils.TypeClassResolver;
import blue.repo.BlueRepositoryModels;

public final class ConversationProcessors {
    private ConversationProcessors() {
    }

    public static Blue registerWith(Blue blue) {
        return registerWith(blue, null);
    }

    public static Blue registerWith(Blue blue, BlueDocumentProcessorOptions options) {
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
        ExpressionAwareMerging.install(blue);
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

    private static SequentialWorkflowRunner workflowRunner(BlueDocumentProcessorOptions options) {
        if (options == null) {
            return null;
        }
        if (options.sequentialWorkflowRunner() != null) {
            return options.sequentialWorkflowRunner();
        }
        JavaScriptRuntime javaScriptRuntime = options.javaScriptRuntime() != null
                ? options.javaScriptRuntime()
                : new NodeQuickJsRuntime();
        BexEngine bexEngine = options.bexEngine() != null
                ? options.bexEngine()
                : BexEngine.builder().build();
        return SequentialWorkflowRunner.withRuntimes(javaScriptRuntime,
                bexEngine,
                options.defaultComputeGasLimit(),
                options.defaultBexExpressionGasLimit(),
                options.processingMetrics());
    }
}
