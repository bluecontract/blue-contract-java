package blue.contract.processor;

import blue.contract.AbstractStepProcessor;
import blue.contract.WorkflowProcessor;
import blue.contract.WorkflowProcessor.ProcessingMode;
import blue.contract.model.WorkflowInstance;
import blue.contract.model.WorkflowProcessingContext;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.JSExecutor;
import blue.language.model.Node;
import blue.language.utils.NodeToObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static blue.language.utils.NodeToObject.Strategy.SIMPLE;
import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class WorkflowFunctionStepProcessor extends AbstractStepProcessor {

    private JSExecutor jsExecutor;

    public WorkflowFunctionStepProcessor(Node step, ExpressionEvaluator expressionEvaluator, JSExecutor jsExecutor) {
        super(step, expressionEvaluator);
        this.jsExecutor = jsExecutor;
    }

    public WorkflowFunctionStepProcessor(Node step, ExpressionEvaluator expressionEvaluator) {
        super(step, expressionEvaluator);
    }

    @Override
    public Optional<WorkflowInstance> handleEvent(Node event, WorkflowProcessingContext context) {
        ProcessingResult result = null;
        try {
            result = processEvent(event, context, ProcessingMode.HANDLE);
        } catch (JSExecutor.JSException ex) {
            return processJSException(ex, context);
        }
        if (result == ProcessingResult.FINISHED) {
            return handleNextStepByOrder(event, context);
        } else if (result == ProcessingResult.CONTINUE) {
            return Optional.of(context.getWorkflowInstance());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorkflowInstance> finalizeEvent(Node event, WorkflowProcessingContext context) {
        ProcessingResult result = null;
        try {
            result = processEvent(event, context, ProcessingMode.FINALIZE);
        } catch (JSExecutor.JSException ex) {
            return processJSException(ex, context);
        }
        if (result == ProcessingResult.FINISHED) {
            return finalizeNextStepByOrder(event, context);
        } else if (result == ProcessingResult.CONTINUE) {
            WorkflowInstance workflowInstance = context.getWorkflowInstance();
            workflowInstance.currentStepName(step.getName());
            return Optional.of(context.getWorkflowInstance());
        } else {
            return Optional.empty();
        }
    }

    private ProcessingResult processEvent(Node event, WorkflowProcessingContext context, ProcessingMode mode) throws JSExecutor.JSException {
        WorkflowInstance workflowInstance = context.getWorkflowInstance();

        if (!workflowInstance.hasNestedWorkflowInstance()) {
            Node subflowDefinition = generateSubflowDefinition(event, context);
            WorkflowInstance nestedInstance = new WorkflowInstance(subflowDefinition);
            nestedInstance.workflow(new Node().properties("steps", subflowDefinition));
            nestedInstance.currentStepName((String) subflowDefinition.get("/0/name"));
            nestedInstance.id(workflowInstance.getId());
            workflowInstance.nestedWorkflowInstance(nestedInstance);
        }

        return processNestedWorkflow(event, context, mode);
    }

    private Node generateSubflowDefinition(Node event, WorkflowProcessingContext context) throws JSExecutor.JSException {
        Node node = step.getProperties().get("initiateStepsCode");
        if (node == null)
            throw new IllegalArgumentException("Attribute \"initiateStepsCode\" is required for Workflow Functions.");
        String code = (String) node.getValue();

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("event", NodeToObject.get(event, SIMPLE));
        bindings.put("steps", context.getWorkflowInstance().getStepResults());
        bindings.put("functionStep", NodeToObject.get(step, SIMPLE));
        bindings.put("contract", (java.util.function.Function<String, Object>) path ->
                context.getContractProcessingContext().accessContract(path, true, true));

        try {
            Object result = jsExecutor.executeScript(code, bindings);
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                if (!resultMap.containsKey("steps"))
                    throw new IllegalArgumentException("\"initiateStepsCode\" did not return any steps.");
                return YAML_MAPPER.convertValue(resultMap.get("steps"), Node.class);
            } else {
                throw new IllegalArgumentException("Unexpected result type from JavaScript execution: " + result.getClass());
            }
        } catch (JSExecutor.JSCriticalException e) {
            throw new IllegalArgumentException("Error executing JS script", e);
        }
    }

    private ProcessingResult processNestedWorkflow(Node event, WorkflowProcessingContext context, ProcessingMode mode) {
        WorkflowInstance workflowInstance = context.getWorkflowInstance();
        WorkflowInstance nestedWorkflowInstance = workflowInstance.getNestedWorkflowInstance();

        WorkflowProcessor workflowProcessor = new WorkflowProcessor(context.getStepProcessorProvider());
        Optional<WorkflowInstance> result = workflowProcessor.processEvent(event, nestedWorkflowInstance, context.getContractProcessingContext(), mode);

        if (result.isPresent()) {
            WorkflowInstance processedSubflow = result.get();
            if (processedSubflow.isCompleted()) {
                workflowInstance.nestedWorkflowInstance(null);
                // map results
                return ProcessingResult.FINISHED;
            } else {
                workflowInstance.nestedWorkflowInstance(processedSubflow);
                return ProcessingResult.CONTINUE;
            }
        }

        return ProcessingResult.NO_RESULT;
    }

    private enum ProcessingResult {
        FINISHED, CONTINUE, NO_RESULT
    }

}