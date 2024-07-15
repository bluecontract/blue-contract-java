package blue.contract;

import blue.language.model.Node;

import java.util.Optional;

@FunctionalInterface
public interface StepProcessorProvider {
    Optional<StepProcessor> getProcessor(Node step);
}
