package blue.coordination.processor;

import blue.coordination.processor.workflow.SequentialWorkflowRunner;
import blue.language.processor.HandlerMatchContext;
import blue.language.processor.HandlerProcessor;
import blue.language.processor.HandlerRegistrationContext;
import blue.language.processor.ProcessorExecutionContext;
import blue.repo.coordination.Operation;
import blue.repo.coordination.SequentialWorkflowOperation;

public final class SequentialWorkflowOperationProcessor implements HandlerProcessor<SequentialWorkflowOperation> {
    private final SequentialWorkflowRunner runner;
    private final OperationRequestMatcher matcher = new OperationRequestMatcher();

    public SequentialWorkflowOperationProcessor() {
        this(new SequentialWorkflowRunner());
    }

    public SequentialWorkflowOperationProcessor(SequentialWorkflowRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
        this.runner = runner;
    }

    @Override
    public Class<SequentialWorkflowOperation> contractType() {
        return SequentialWorkflowOperation.class;
    }

    @Override
    public String deriveChannel(SequentialWorkflowOperation contract, HandlerRegistrationContext context) {
        Operation operation = context.contractAs(contract.getOperation(), Operation.class);
        String channel = operation != null ? trimToNull(operation.getChannel()) : null;
        if (channel != null && !context.hasContract(channel)) {
            throw new IllegalStateException("Sequential workflow operation '" + context.handlerKey()
                    + "' references unknown channel '" + channel + "'");
        }
        return channel;
    }

    @Override
    public boolean matches(SequentialWorkflowOperation contract, HandlerMatchContext context) {
        return matcher.matches(contract, context);
    }

    @Override
    public void execute(SequentialWorkflowOperation contract, ProcessorExecutionContext context) {
        runner.execute(contract, context);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
