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
        processorMap.put("3UXhfjDZ8EMopVJrDxS8Gf2USfLZvzFGpzyzconyzAkm", node -> new ExpectEventStepProcessor(node, expressionEvaluator));
        processorMap.put("6sdEGwtrVJhdto5CsDzm81YrJtHTZrdsenZkyCWJLniU", node -> new TriggerEventStepProcessor(node, expressionEvaluator));
        processorMap.put("DpdjTNXQdgWGxDyB1LLUNFvxSNNM9L9qGMoKZxzYMDoB", node -> new UpdateStepProcessor(node, expressionEvaluator));
        processorMap.put("FF88BRKtRXQ2cCCBR28cFUB5mHdPsWjj8gVBgz4VmQm7", node -> new InitializeLocalContractStepProcessor(node, expressionEvaluator));
        processorMap.put("EZjDfHEnvvwhP14FXdzLSBXBW9BcanYefW9mRfZNLh7D", node -> new WorkflowFunctionStepProcessor(node, expressionEvaluator, jsExecutor));
        processorMap.put("CFKAD5Up8XpNyPHwRBEwiwSUdfFUoGqVVsW29k6te88p", node -> new JSCodeStepProcessor(node, expressionEvaluator, jsExecutor, blue));

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
