package blue.contract;

import blue.contract.debug.DebugContext;
import blue.contract.debug.DebugInfo;
import blue.contract.debug.DebugContextAware;
import blue.contract.exception.ContractProcessingException;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.event.ContractProcessingEvent;
import blue.contract.model.event.FatalErrorEvent;
import blue.contract.utils.Events;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.ExpressionEvaluator.ExpressionScope;
import blue.contract.utils.JSExecutor;
import blue.contract.utils.JSExecutor.JSCriticalException;
import blue.contract.utils.JSExecutor.RejectAndAwaitNextEventException;
import blue.contract.utils.JSExecutor.TerminateContractWithErrorException;
import blue.contract.utils.Workflows;
import blue.language.model.Node;

import java.util.Optional;
import java.util.function.BiFunction;

import static blue.contract.WorkflowProcessor.ProcessingMode.FINALIZE;
import static blue.contract.WorkflowProcessor.ProcessingMode.HANDLE;
import static blue.language.utils.UncheckedObjectMapper.JSON_MAPPER;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public abstract class AbstractStepProcessor implements StepProcessor, DebugContextAware {

    protected Node step;
    protected final ExpressionEvaluator expressionEvaluator;
    protected DebugContext debugContext;

    public AbstractStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        this.step = step;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {
        if (shouldExecuteStep(context)) {
            return executeHandleStep(event, context);
        } else {
            getDebugContext().skipWorkflowStep(HANDLE, getStepName().orElse("<StepWithNoName>"));
            return handleNextStepByOrder(event, context);
        }
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext context) {
        if (shouldExecuteStep(context)) {
            return executeFinalizeStep(event, context);
        } else {
            getDebugContext().skipWorkflowStep(FINALIZE, getStepName().orElse("<StepWithNoName>"));
            return finalizeNextStepByOrder(event, context);
        }
    }
    protected abstract Optional<WorkflowInstance> executeHandleStep(Node event, WorkflowProcessingContext context);
    protected abstract Optional<WorkflowInstance> executeFinalizeStep(Node event, WorkflowProcessingContext context);

    protected Optional<WorkflowInstance> handleNextStepByOrder(Node event, WorkflowProcessingContext context) {
        return processNextStep(event, context, (processor, ctx) -> processor.handleEvent(event, context));
    }

    protected Optional<WorkflowInstance> finalizeNextStepByOrder(Node event, WorkflowProcessingContext context) {
        return processNextStep(event, context, (processor, ctx) -> processor.finalizeEvent(event, context));
    }

    private Optional<WorkflowInstance> processNextStep(Node event, WorkflowProcessingContext context,
                                                       BiFunction<StepProcessor, Node, Optional<WorkflowInstance>> stepFunction) {
        Optional<Node> nextStep;
        Optional<StepProcessor> stepProcessor;

        do {
            nextStep = Workflows.getNextStepByOrder(step, context.getWorkflowInstance().getWorkflow());
            if (!nextStep.isPresent()) {
                return completeWorkflow(context.getWorkflowInstance());
            }

            stepProcessor = context.getStepProcessorProvider().getProcessor(nextStep.get());
            if (!stepProcessor.isPresent()) {
                throw new IllegalArgumentException("No StepProcessor found for event: " +
                                                   JSON_MAPPER.disable(INDENT_OUTPUT).writeValueAsString(event));
            }

            AbstractStepProcessor abstractStepProcessor = (AbstractStepProcessor) stepProcessor.get();
            if (abstractStepProcessor.shouldExecuteStep(context)) {
                abstractStepProcessor.setDebugContext(getDebugContext());
                return stepFunction.apply(abstractStepProcessor, event);
            }

            step = nextStep.get();
        } while (true);
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

    protected boolean shouldExecuteStep(WorkflowProcessingContext context) {
        if (step.getProperties().containsKey("condition")) {
            String value = (String) step.getProperties().get("condition").getValue();
            if (value != null) {
                Node result = (Node) evaluateExpression(value, context);
                return !(result.getValue() != null && result.getValue() instanceof Boolean && Boolean.FALSE.equals(result.getValue()));
            }
        }
        return true;
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

    protected Optional<WorkflowInstance> processJSException(JSExecutor.JSException ex, WorkflowProcessingContext context) {
        ContractProcessingContext contractProcessingContext = context.getContractProcessingContext();

        if (ex instanceof RejectAndAwaitNextEventException) {
            context.rollbackTransaction();
            return Optional.empty();
        } else if (ex instanceof TerminateContractWithErrorException) {
            terminateContractWithError(ex, context, contractProcessingContext);
            return Optional.empty();
        } else if (ex instanceof JSCriticalException) {
            terminateContractWithError(ex, context, contractProcessingContext);
            return Optional.empty();
        }

        terminateContractWithError(ex, context, contractProcessingContext);
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
                .stackTrace(((JSCriticalException) ex).getJsStackTrace());
        Node errorEventNode = contractProcessingContext.getBlue().objectToNode(errorEvent);
        ContractProcessingEvent processingEvent = Events.prepareContractProcessingEvent(errorEventNode, step.getName(), workflowProcessingContext);
        Node processingEventNode = contractProcessingContext.getBlue().objectToNode(processingEvent);
        workflowProcessingContext.getContractProcessingContext().getEmittedEvents().add(processingEventNode);

        throw new ContractProcessingException("Critical JS error", ex);
    }

    @Override
    public DebugContext getDebugContext() {
        return debugContext;
    }

    public void setDebugContext(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

}