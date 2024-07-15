package blue.contract;

import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.utils.Workflows;
import blue.language.model.Node;

import java.util.Optional;
import java.util.function.BiFunction;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public abstract class AbstractStepProcessor implements StepProcessor {

    protected Node step;

    public AbstractStepProcessor(Node step) {
        this.step = step;
    }

    protected Optional<WorkflowInstance> handleNextStepByOrder(Node event, WorkflowProcessingContext context) {
        return processNextStep(event, context, (processor, ctx) -> processor.handleEvent(event, context));
    }

    protected Optional<WorkflowInstance> finalizeNextStepByOrder(Node event, WorkflowProcessingContext context) {
        return processNextStep(event, context, (processor, ctx) -> processor.finalizeEvent(event, context));
    }

    private Optional<WorkflowInstance> processNextStep(Node event, WorkflowProcessingContext context,
                                                       BiFunction<StepProcessor, Node, Optional<WorkflowInstance>> stepFunction) {
        Optional<Node> nextStep = Workflows.getNextStepByOrder(step, context.getWorkflowInstance().getWorkflow());
        if (!nextStep.isPresent())
            return finishWorkflow(context.getWorkflowInstance());

        Optional<StepProcessor> stepProcessor = context.getStepProcessorProvider().getProcessor(nextStep.get());
        if (!stepProcessor.isPresent())
            throw new IllegalArgumentException("No StepProcessor found for event: " +
                    JSON_MAPPER.disable(INDENT_OUTPUT).writeValueAsString(event));

        return stepFunction.apply(stepProcessor.get(), event);
    }

    protected Optional<WorkflowInstance> finishWorkflow(WorkflowInstance workflowInstance) {
        workflowInstance.finished(true);
        return Optional.of(workflowInstance);
    }

    public Node getStep() {
        return step;
    }

    public Optional<String> getStepName() {
        return Optional.ofNullable(step.getName());
    }

}