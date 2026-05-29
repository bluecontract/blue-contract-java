package blue.coordination.processor;

import blue.coordination.processor.workflow.SequentialWorkflowRunner;
import blue.language.processor.HandlerMatchContext;
import blue.language.processor.HandlerProcessor;
import blue.language.processor.HandlerRegistrationContext;
import blue.language.processor.ProcessorExecutionContext;
import blue.repo.coordination.SequentialWorkflow;

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
    public String deriveChannel(SequentialWorkflow contract, HandlerRegistrationContext context) {
        return contract != null ? contract.getChannel() : null;
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
