package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.JSExecutor;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.NodeToObject;
import blue.language.utils.limits.PathLimits;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static blue.language.utils.NodeToObject.Strategy.SIMPLE;

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
            processEvent(event, context);
        } catch (JSExecutor.JSException ex) {
            return processJSException(ex, context);
        }
        return handleNextStepByOrder(event, context);
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext context) {
        try {
            processEvent(event, context);
        } catch (JSExecutor.JSException ex) {
            return processJSException(ex, context);
        }
        return finalizeNextStepByOrder(event, context);
    }

    private void processEvent(Node event, WorkflowProcessingContext context) throws JSExecutor.JSException {

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("event", NodeToObject.get(event, SIMPLE));
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
    }
}