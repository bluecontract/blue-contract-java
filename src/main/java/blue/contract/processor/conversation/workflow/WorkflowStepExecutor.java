package blue.contract.processor.conversation.workflow;

import blue.repo.v1_2_0.conversation.SequentialWorkflowStep;

public interface WorkflowStepExecutor<T extends SequentialWorkflowStep> {
    boolean supports(SequentialWorkflowStep step);

    WorkflowStepResult execute(T step, StepExecutionContext context);
}
