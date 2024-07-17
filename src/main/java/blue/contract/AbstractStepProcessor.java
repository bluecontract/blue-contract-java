package blue.contract;

import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.ExpressionEvaluator.ExpressionScope;
import blue.contract.utils.Workflows;
import blue.language.model.Node;

import java.util.Optional;
import java.util.function.BiFunction;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public abstract class AbstractStepProcessor implements StepProcessor {

    protected Node step;
    protected final ExpressionEvaluator expressionEvaluator;

    public AbstractStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        this.step = step;
        this.expressionEvaluator = expressionEvaluator;
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

    protected Object evaluateExpression(Object potentialExpression, WorkflowProcessingContext context) {
        return evaluateExpression(potentialExpression, context, ExpressionScope.GLOBAL);
    }

    protected Object evaluateExpression(Object potentialExpression, WorkflowProcessingContext context, ExpressionScope scope) {
        return expressionEvaluator.evaluateIfExpression(potentialExpression, context, scope);
    }

    protected Node evaluateExpressionsRecursively(Node node, WorkflowProcessingContext context) {
        return evaluateExpressionsRecursively(node, context, ExpressionScope.GLOBAL);
    }

    protected Node evaluateExpressionsRecursively(Node node, WorkflowProcessingContext context, ExpressionScope scope) {
        return expressionEvaluator.processNodeRecursively(node, context, scope);
    }

}