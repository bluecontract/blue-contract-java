package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.ContractInstance;
import blue.contract.model.ContractProcessingContext;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.JSExecutor;
import blue.contract.utils.JSExecutor.ContractCompleteResult;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToMapListOrValue;
import blue.language.utils.limits.PathLimits;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static blue.language.utils.NodeToMapListOrValue.Strategy.SIMPLE;

public class JSCodeStepProcessor extends AbstractStepProcessor {

    private final String code;
    private final JSExecutor jsExecutor;

    public JSCodeStepProcessor(Node step, ExpressionEvaluator expressionEvaluator, JSExecutor jsExecutor, Blue blue) {
        super(step, expressionEvaluator);
        blue.extend(step, PathLimits.withSinglePath("/*"));
        this.code = (String) step.getProperties().get("code").getValue();
        this.jsExecutor = jsExecutor;
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {
        try {
            Object result = processEvent(event, context);
            if (result instanceof ContractCompleteResult) {
                return handleContractComplete((ContractCompleteResult) result, context);
            }
            return handleNextStepByOrder(event, context);
        } catch (JSExecutor.JSException ex) {
            return processJSException(ex, context);
        }
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext context) {
        try {
            Object result = processEvent(event, context);
            if (result instanceof ContractCompleteResult) {
                return handleContractComplete((ContractCompleteResult) result, context);
            }
            return finalizeNextStepByOrder(event, context);
        } catch (JSExecutor.JSException ex) {
            return processJSException(ex, context);
        }
    }

    private Object processEvent(Node event, WorkflowProcessingContext context) throws JSExecutor.JSException {

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("event", NodeToMapListOrValue.get(event, SIMPLE));
        bindings.put("steps", context.getWorkflowInstance().getStepResults());
        bindings.put("contract", (java.util.function.Function<String, Object>) path ->
                context.getContractProcessingContext().accessContract(path, true, true));

        Object result = jsExecutor.executeScript(code, bindings);

        Optional<String> stepName = getStepName();
        if (stepName.isPresent()) {
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                context.getWorkflowInstance().getStepResults().put(stepName.get(), resultMap);
            } else {
                throw new IllegalArgumentException("Unexpected result type from JavaScript execution: " + result.getClass());
            }
        }
        return result;
    }

    private Optional<WorkflowInstance> handleContractComplete(ContractCompleteResult result, WorkflowProcessingContext context) {
        ContractProcessingContext contractProcessingContext = context.getContractProcessingContext();
        ContractInstance currentInstance = contractProcessingContext.getCurrentContractInstance();
        currentInstance.getProcessingState().completed(true);
        if (currentInstance.getId() == ContractInstance.ROOT_INSTANCE_ID) {
            contractProcessingContext.completed(true);
        }
        return Optional.of(context.getWorkflowInstance().completed(true));
    }

}