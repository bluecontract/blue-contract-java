package blue.contract.model;

import blue.language.model.Node;

import java.util.HashMap;
import java.util.Map;

public class WorkflowInstance implements Cloneable {
    private int id;
    private Node workflow;
    private String currentStepName;
    private Map<String, Object> stepResults;
    private boolean completed;
    private WorkflowInstance nestedWorkflowInstance;

    public WorkflowInstance() {
    }

    public WorkflowInstance(Node workflow) {
        this.workflow = workflow;
        this.stepResults = new HashMap<>();
    }

    public int getId() {
        return id;
    }

    public Node getWorkflow() {
        return workflow;
    }

    public String getCurrentStepName() {
        return currentStepName;
    }

    public Map<String, Object> getStepResults() {
        return stepResults;
    }

    public boolean isCompleted() {
        return completed;
    }

    public WorkflowInstance getNestedWorkflowInstance() {
        return nestedWorkflowInstance;
    }

    public WorkflowInstance id(int id) {
        this.id = id;
        return this;
    }

    public WorkflowInstance workflow(Node workflow) {
        this.workflow = workflow;
        return this;
    }

    public WorkflowInstance currentStepName(String currentStepName) {
        this.currentStepName = currentStepName;
        return this;
    }

    public WorkflowInstance stepResults(Map<String, Object> stepResults) {
        this.stepResults = stepResults;
        return this;
    }

    public WorkflowInstance processingContext(Map<String, Object> processingContext) {
        this.stepResults = processingContext;
        return this;
    }

    public WorkflowInstance completed(boolean completed) {
        this.completed = completed;
        return this;
    }

    public WorkflowInstance nestedWorkflowInstance(WorkflowInstance nestedWorkflowInstance) {
        this.nestedWorkflowInstance = nestedWorkflowInstance;
        return this;
    }

    public boolean hasNestedWorkflowInstance() {
        return nestedWorkflowInstance != null;
    }

    @Override
    public WorkflowInstance clone() {
        try {
            WorkflowInstance cloned = (WorkflowInstance) super.clone();
            cloned.workflow = this.workflow != null ? this.workflow.clone() : null;
            if (this.stepResults != null) {
                cloned.stepResults = new HashMap<>(this.stepResults);
            }
            if (this.nestedWorkflowInstance != null) {
                cloned.nestedWorkflowInstance = this.nestedWorkflowInstance.clone();
            }
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("WorkflowInstance should be cloneable", e);
        }
    }

}
