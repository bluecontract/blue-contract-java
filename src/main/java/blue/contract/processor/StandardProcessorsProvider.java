package blue.contract.processor;

import blue.contract.StepProcessor;
import blue.contract.StepProcessorProvider;
import blue.contract.model.WorkflowFunction;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.JSExecutor;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.BlueIds;
import blue.language.utils.Types;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class StandardProcessorsProvider implements StepProcessorProvider {

    private Blue blue;

    public StandardProcessorsProvider(Blue blue) {
        this.blue = blue;
    }

    @Override
    public Optional<StepProcessor> getProcessor(Node step) {
        if (step.getType() == null || step.getType().getName() == null) {
            return Optional.empty();
        }

        JSExecutor jsExecutor = new JSExecutor();
        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(jsExecutor);

        Optional<StepProcessor> workflowFunctionProcessor = getWorkflowFunctionProcessor(step, expressionEvaluator, jsExecutor);
        if (workflowFunctionProcessor.isPresent()) {
            return workflowFunctionProcessor;
        }

        Map<String, Function<Node, StepProcessor>> processorMap = new HashMap<>();
        processorMap.put("Hello Event", node -> new HelloEventProcessor(node, expressionEvaluator));
        processorMap.put("Expect Event Step", node -> new ExpectEventStepProcessor(node, expressionEvaluator));
        processorMap.put("Trigger Event Step", node -> new TriggerEventStepProcessor(node, expressionEvaluator));
        processorMap.put("Update Step", node -> new UpdateStepProcessor(node, expressionEvaluator));
        processorMap.put("Dummy Code", node -> new DummyCodeStepProcessor(node, expressionEvaluator));
        processorMap.put("Initialize Local Contract Step", node -> new InitializeLocalContractStepProcessor(node, expressionEvaluator));
        processorMap.put("Workflow Function Step", node -> new WorkflowFunctionStepProcessor(node, expressionEvaluator, jsExecutor));
        processorMap.put("JavaScript Code Step", node -> new JSCodeStepProcessor(node, expressionEvaluator, jsExecutor, blue));

        return Optional.ofNullable(processorMap.get(step.getType().getName())).map(func -> func.apply(step));
    }

    private Optional<StepProcessor> getWorkflowFunctionProcessor(Node step, ExpressionEvaluator expressionEvaluator, JSExecutor jsExecutor) {
        String workflowFunctionBlueId = BlueIds.getBlueId(WorkflowFunction.class)
                .orElseThrow(() -> new IllegalArgumentException("No @BlueId annotation for WorkflowFunction class."));
        if (Types.isSubtype(step, new Node().blueId(workflowFunctionBlueId), blue.getNodeProvider())) {
//        if (blue.isNodeSubtypeOf(step, new Node().blueId(workflowFunctionBlueId)))
            return Optional.of(new WorkflowFunctionStepProcessor(step, expressionEvaluator, jsExecutor));
        }
        return Optional.empty();
    }
}
