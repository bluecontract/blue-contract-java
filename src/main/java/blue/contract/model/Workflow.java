package blue.contract.model;

import blue.contract.model.step.WorkflowStep;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import java.util.List;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class Workflow {
    private String name;
    private Node trigger;
    private List<? extends WorkflowStep> steps;

    public String getName() {
        return name;
    }

    public Workflow name(String name) {
        this.name = name;
        return this;
    }

    public Node getTrigger() {
        return trigger;
    }

    public Workflow trigger(Node trigger) {
        this.trigger = trigger;
        return this;
    }

    public List<? extends WorkflowStep> getSteps() {
        return steps;
    }

    public Workflow steps(List<? extends WorkflowStep> steps) {
        this.steps = steps;
        return this;
    }
}
