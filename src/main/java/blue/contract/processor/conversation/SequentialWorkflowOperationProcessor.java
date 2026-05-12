package blue.contract.processor.conversation;

import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;
import blue.language.processor.HandlerMatchContext;
import blue.language.processor.HandlerProcessor;
import blue.language.processor.HandlerRegistrationContext;
import blue.language.processor.ProcessorExecutionContext;
import blue.repo.v1_2_0.conversation.Operation;
import blue.repo.v1_2_0.conversation.SequentialWorkflowOperation;

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
        return operation != null ? operation.getChannel() : null;
    }

    @Override
    public boolean matches(SequentialWorkflowOperation contract, HandlerMatchContext context) {
        return matcher.matches(contract, context);
    }

    @Override
    public void execute(SequentialWorkflowOperation contract, ProcessorExecutionContext context) {
        runner.execute(contract, context);
    }
}
