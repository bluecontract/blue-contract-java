package blue.contract.processor.conversation;

import blue.contract.processor.conversation.workflow.SequentialWorkflowRunner;
import blue.language.processor.HandlerMatchContext;
import blue.language.processor.HandlerProcessor;
import blue.language.processor.ProcessorExecutionContext;
import blue.repo.v1_3_0.conversation.SequentialWorkflow;

public final class SequentialWorkflowProcessor implements HandlerProcessor<SequentialWorkflow> {
    private final SequentialWorkflowRunner runner;

    public SequentialWorkflowProcessor() {
        this(new SequentialWorkflowRunner());
    }

    public SequentialWorkflowProcessor(SequentialWorkflowRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
        this.runner = runner;
    }

    @Override
    public Class<SequentialWorkflow> contractType() {
        return SequentialWorkflow.class;
    }

    @Override
    public boolean matches(SequentialWorkflow contract, HandlerMatchContext context) {
        return contract.getEvent() == null || context.matchesEventPattern(contract.getEvent());
    }

    @Override
    public void execute(SequentialWorkflow contract, ProcessorExecutionContext context) {
        runner.execute(contract, context);
    }
}
