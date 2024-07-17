package blue.contract.processor;

import blue.contract.StepProcessor;
import blue.contract.StepProcessorProvider;
import blue.contract.utils.ExpressionEvaluator;
import blue.contract.utils.JSExecutor;
import blue.language.model.Node;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class StandardProcessorsProvider implements StepProcessorProvider {
    @Override
    public Optional<StepProcessor> getProcessor(Node step) {
        if (step.getType() == null || step.getType().getName() == null) {
            return Optional.empty();
        }

        JSExecutor jsExecutor = new JSExecutor();
        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(jsExecutor);

        Map<String, Function<Node, StepProcessor>> processorMap = new HashMap<>();
        processorMap.put("Hello Event", node -> new HelloEventProcessor(node, expressionEvaluator));
        processorMap.put("Expect Event Step", node -> new ExpectEventStepProcessor(node, expressionEvaluator));
        processorMap.put("Trigger Event Step", node -> new TriggerEventStepProcessor(node, expressionEvaluator));
        processorMap.put("Update Step", node -> new UpdateStepProcessor(node, expressionEvaluator));
        processorMap.put("Dummy Code", node -> new DummyCodeStepProcessor(node, expressionEvaluator));
        processorMap.put("Initialize Local Contract Step", node -> new InitializeLocalContractStepProcessor(node, expressionEvaluator));
        processorMap.put("JavaScript Code Step", node -> new JSCodeStepProcessor(node, expressionEvaluator, jsExecutor));

        return Optional.ofNullable(processorMap.get(step.getType().getName())).map(func -> func.apply(step));
    }
}
