package blue.coordination.processor.workflow;

import blue.repo.coordination.SequentialWorkflowStep;

public interface WorkflowStepExecutor<T extends SequentialWorkflowStep> {
    boolean supports(SequentialWorkflowStep step);

    WorkflowStepResult execute(T step, StepExecutionContext context);
}
