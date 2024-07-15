package blue.contract.utils;

import blue.language.model.Node;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class Workflows {

    public static Node getNextStepByName(Node currentStep, Node workflow) {
        return null;
    }

    public static Optional<Node> getNextStepByOrder(Node currentStep, Node workflow) {
        Node trigger = workflow.getProperties().get("trigger");
        Node steps = workflow.getProperties().get("steps");

        if (currentStep == trigger)
            return Optional.of(steps.getItems().get(0));

        List<Node> stepItems = steps.getItems();

        int currentIndex = IntStream.range(0, stepItems.size())
                .filter(i -> stepItems.get(i).equals(currentStep))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Current step not found in the workflow steps."));

        if (currentIndex == stepItems.size() - 1)
            return Optional.empty();

        return Optional.of(stepItems.get(currentIndex + 1));
    }

    public static Optional<Node> getStepByName(String currentStepName, Node workflow) {
        Node steps = workflow.getProperties().get("steps");
        List<Node> stepItems = steps.getItems();

        return stepItems.stream()
                .filter(step -> currentStepName.equals(step.getName()))
                .findFirst();
    }
}
