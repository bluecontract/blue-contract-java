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
        if (step.getType() == null || step.getType().getBlueId() == null) {
            return Optional.empty();
        }

        JSExecutor jsExecutor = new JSExecutor(blue);
        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(jsExecutor);

        Optional<StepProcessor> workflowFunctionProcessor = getWorkflowFunctionProcessor(step, expressionEvaluator, jsExecutor);
        if (workflowFunctionProcessor.isPresent()) {
            return workflowFunctionProcessor;
        }

        Map<String, Function<Node, StepProcessor>> processorMap = new HashMap<>();
        processorMap.put("81yruzcExdbod4xZ49qxZpnaWEnDEiGZ7xe5sh13AQ7g", node -> new ExpectEventStepProcessor(node, expressionEvaluator));
        processorMap.put("3uFwcdCx8Sdw43hGbBL3t9YGRocLbAewHzzxbrKAkUKF", node -> new TriggerEventStepProcessor(node, expressionEvaluator));
        processorMap.put("6PsXX3HF74kESc6eg58z8kNwzqD64xQ3PjGPUppzcWg6", node -> new UpdateStepProcessor(node, expressionEvaluator));
        processorMap.put("D7bzHri8CT5j7aZtWVLcTAXkVdrrhe9inGbQopszrtbB", node -> new InitializeLocalContractStepProcessor(node, expressionEvaluator));
        processorMap.put("8ZZiA8FgJC1scybYXCVt4Qf9Zh9LQGMLChDtVmDfZh9o", node -> new WorkflowFunctionStepProcessor(node, expressionEvaluator, jsExecutor));
        processorMap.put("5TrdtnYzrxenA6HLujs6z2Q5gLcS9heyrr3HpBNSbeFb", node -> new JSCodeStepProcessor(node, expressionEvaluator, jsExecutor, blue));

        return Optional.ofNullable(processorMap.get(step.getType().getBlueId())).map(func -> func.apply(step));
    }

    private Optional<StepProcessor> getWorkflowFunctionProcessor(Node step, ExpressionEvaluator expressionEvaluator, JSExecutor jsExecutor) {
        String workflowFunctionBlueId = BlueIds.getBlueId(WorkflowFunction.class)
                .orElseThrow(() -> new IllegalArgumentException("No @BlueId annotation for WorkflowFunction class."));
        if (Types.isSubtype(step, new Node().blueId(workflowFunctionBlueId), blue.getNodeProvider())) {
            return Optional.of(new WorkflowFunctionStepProcessor(step, expressionEvaluator, jsExecutor));
        }
        return Optional.empty();
    }
}
