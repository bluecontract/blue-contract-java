package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.utils.JSExecutor;
import blue.language.model.Node;
import blue.language.utils.NodeToObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static blue.language.utils.NodeToObject.Strategy.SIMPLE;

public class JSCodeStepProcessor extends AbstractStepProcessor {

    private final String code;

    public JSCodeStepProcessor(Node step) throws IOException {
        super(step);
        this.code = (String) step.getProperties().get("code").getValue();
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return handleNextStepByOrder(event, context);
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext context) {
        processEvent(event, context);
        return finalizeNextStepByOrder(event, context);
    }

    private void processEvent(Node event, WorkflowProcessingContext workflowProcessingContext) {
        JSExecutor jsExecutor = new JSExecutor(workflowProcessingContext.getContractProcessingContext());

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("event", NodeToObject.get(event, SIMPLE));
        bindings.put("steps", workflowProcessingContext.getWorkflowInstance().getStepResults());
        bindings.put("contract", new ContractFunction(jsExecutor));

        try {
            Object result = jsExecutor.executeScript(code, bindings);

            Optional<String> stepName = getStepName();
            if (stepName.isPresent()) {
                if (result instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) result;
                    workflowProcessingContext.getWorkflowInstance().getStepResults().put(stepName.get(), resultMap);
                } else {
                    throw new IllegalArgumentException("Unexpected result type from JavaScript execution");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Error executing JS script", e);
        }
    }
}