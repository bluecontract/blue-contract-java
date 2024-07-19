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

    protected Object evaluateExpression(Object potentialExpression, WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        if (potentialExpression instanceof String) {
            return expressionEvaluator.evaluate((String) potentialExpression, context, scope, resolveFinalLink);
        }
        return potentialExpression;
    }

    protected Object evaluateExpression(Object potentialExpression, WorkflowProcessingContext context, ExpressionScope scope) {
        return evaluateExpression(potentialExpression, context, scope, true);
    }

    protected Object evaluateExpression(Object potentialExpression, WorkflowProcessingContext context) {
        return evaluateExpression(potentialExpression, context, ExpressionScope.GLOBAL, true);
    }

    protected Object evaluateExpressionWithoutFinalLink(Object potentialExpression, WorkflowProcessingContext context) {
        return evaluateExpression(potentialExpression, context, ExpressionScope.GLOBAL, false);
    }

    protected Node evaluateExpressionsRecursively(Node node, WorkflowProcessingContext context, ExpressionScope scope, boolean resolveFinalLink) {
        return expressionEvaluator.processNodeRecursively(node, context, scope, resolveFinalLink);
    }

    protected Node evaluateExpressionsRecursively(Node node, WorkflowProcessingContext context, ExpressionScope scope) {
        return evaluateExpressionsRecursively(node, context, scope, true);
    }

    protected Node evaluateExpressionsRecursively(Node node, WorkflowProcessingContext context) {
        return evaluateExpressionsRecursively(node, context, ExpressionScope.GLOBAL, true);
    }

    protected Node evaluateExpressionsRecursivelyWithoutFinalLink(Node node, WorkflowProcessingContext context) {
        return evaluateExpressionsRecursively(node, context, ExpressionScope.GLOBAL, false);
    }
    
}