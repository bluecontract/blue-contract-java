package blue.contract.processor;

import blue.contract.StepProcessor;
import blue.contract.StepProcessorProvider;
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

        Map<String, Function<Node, StepProcessor>> processorMap = new HashMap<>();
        processorMap.put("Hello Event", HelloEventProcessor::new);
        processorMap.put("Expect Event Step", ExpectEventStepProcessor::new);
        processorMap.put("Trigger Event Step", TriggerEventStepProcessor::new);
        processorMap.put("Update Step", UpdateStepProcessor::new);
        processorMap.put("Dummy Code", DummyCodeStepProcessor::new);
        processorMap.put("Initialize Local Contract Step", InitializeLocalContractStepProcessor::new);
        processorMap.put("JavaScript Code Step", node -> {
            try {
                return new JSCodeStepProcessor(node);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return Optional.ofNullable(processorMap.get(step.getType().getName())).map(func -> func.apply(step));
    }
}
