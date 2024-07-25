package blue.contract.model;

import java.util.List;
import java.util.stream.Collectors;

public class ProcessingState implements Cloneable {
    private int startedWorkflowCount;
    private int startedLocalContractCount;
    private List<WorkflowInstance> workflowInstances;
    private List<ContractInstance> localContractInstances;
    private boolean completed;
    private boolean terminatedWithError;

    public int getStartedWorkflowCount() {
        return startedWorkflowCount;
    }

    public int getStartedLocalContractCount() {
        return startedLocalContractCount;
    }

    public List<WorkflowInstance> getWorkflowInstances() {
        return workflowInstances;
    }

    public List<ContractInstance> getLocalContractInstances() {
        return localContractInstances;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isTerminatedWithError() {
        return terminatedWithError;
    }

    public ProcessingState startedWorkflowsCount(int startedWorkflowCount) {
        this.startedWorkflowCount = startedWorkflowCount;
        return this;
    }

    public ProcessingState startedLocalContractsCount(int startedLocalContractCount) {
        this.startedLocalContractCount = startedLocalContractCount;
        return this;
    }

    public ProcessingState workflowInstances(List<WorkflowInstance> workflowInstances) {
        this.workflowInstances = workflowInstances;
        return this;
    }

    public ProcessingState localContractInstances(List<ContractInstance> localContractInstances) {
        this.localContractInstances = localContractInstances;
        return this;
    }

    public ProcessingState completed(boolean completed) {
        this.completed = completed;
        return this;
    }

    public ProcessingState terminatedWithError(boolean terminatedWithError) {
        this.terminatedWithError = terminatedWithError;
        return this;
    }

    @Override
    public ProcessingState clone() {
        try {
            ProcessingState cloned = (ProcessingState) super.clone();
            if (this.workflowInstances != null) {
                cloned.workflowInstances = this.workflowInstances.stream()
                        .map(WorkflowInstance::clone)
                        .collect(Collectors.toList());
            }
            if (this.localContractInstances != null) {
                cloned.localContractInstances = this.localContractInstances.stream()
                        .map(ContractInstance::clone)
                        .collect(Collectors.toList());
            }
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("ProcessingState should be cloneable", e);
        }
    }
}