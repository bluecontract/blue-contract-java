package blue.contract;

import blue.contract.exception.ContractProcessingException;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.model.event.FatalErrorEvent;
import blue.contract.utils.Events;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.ExpressionEvaluator.ExpressionScope;
import blue.contract.utils.JSExecutor;
import blue.contract.utils.Workflows;
import blue.language.model.Node;

import java.util.Optional;
import java.util.function.BiFunction;

import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;
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
            return completeWorkflow(context.getWorkflowInstance());

        Optional<StepProcessor> stepProcessor = context.getStepProcessorProvider().getProcessor(nextStep.get());
        if (!stepProcessor.isPresent())
            throw new IllegalArgumentException("No StepProcessor found for event: " +
                                               JSON_MAPPER.disable(INDENT_OUTPUT).writeValueAsString(event));

        return stepFunction.apply(stepProcessor.get(), event);
    }

    protected Optional<WorkflowInstance> completeWorkflow(WorkflowInstance workflowInstance) {
        workflowInstance.completed(true);
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

    protected Optional<WorkflowInstance> processJSException(JSExecutor.JSException ex,
                                                            WorkflowProcessingContext workflowProcessingContext)  {
        ContractProcessingContext contractProcessingContext = workflowProcessingContext.getContractProcessingContext();
        if (ex instanceof JSExecutor.JSCriticalException) {
            terminateContractWithError(ex, workflowProcessingContext, contractProcessingContext);
        } else if (ex instanceof JSExecutor.ProcessControlException) {
            JSExecutor.ProcessControlException pce = (JSExecutor.ProcessControlException) ex;
            switch (pce.getControlAction()) {
                case "completeContract":
                    ContractInstance currentInstance = contractProcessingContext.getCurrentContractInstance();
                    currentInstance.getProcessingState().completed(true);
                    if (currentInstance.getId() == ContractInstance.ROOT_INSTANCE_ID) {
                        contractProcessingContext.completed(true);
                    }
                    return Optional.of(workflowProcessingContext.getWorkflowInstance().completed(true));
                case "returnFromWorkflow":
                    workflowProcessingContext.rollbackTransaction();
                    return Optional.empty();
                case "terminateContractWithError":
                    terminateContractWithError(ex, workflowProcessingContext, contractProcessingContext);
            }
        }
        return Optional.empty();
    }

    private void terminateContractWithError(JSExecutor.JSException ex, WorkflowProcessingContext workflowProcessingContext,
                                            ContractProcessingContext contractProcessingContext) {
        contractProcessingContext.terminatedWithError(true);
        if (!contractProcessingContext.getContractInstances().isEmpty()) {
            contractProcessingContext.getCurrentContractInstance().getProcessingState().terminatedWithError(true);
            contractProcessingContext.getRootContractInstance().getProcessingState().terminatedWithError(true);
        }

        FatalErrorEvent errorEvent = new FatalErrorEvent()
                .errorMessage("Critical, irrecoverable JS error, contract terminated with error.")
                .stackTrace(((JSExecutor.JSCriticalException) ex).getJsStackTrace());
        Node errorEventNode = YAML_MAPPER.convertValue(errorEvent, Node.class);
        ContractProcessingEvent processingEvent = Events.prepareContractProcessingEvent(errorEventNode, step.getName(), workflowProcessingContext);
        Node processingEventNode = YAML_MAPPER.convertValue(processingEvent, Node.class);
        workflowProcessingContext.getContractProcessingContext().getEmittedEvents().add(processingEventNode);

        throw new ContractProcessingException("Critical JS error", ex);
    }

}