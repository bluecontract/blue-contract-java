package blue.contract.model.event;

import blue.language.model.BlueId;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class WorkflowInstanceStartedEvent {
    private Integer workflowInstanceId;
    private Integer contractInstanceId;
    private String currentStepName;
    @BlueId
    private String initiateContractEntry;
    @BlueId
    private String initiateContractProcessingEntry;

    public Integer getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public WorkflowInstanceStartedEvent workflowInstanceId(Integer workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
        return this;
    }

    public Integer getContractInstanceId() {
        return contractInstanceId;
    }

    public WorkflowInstanceStartedEvent contractInstanceId(Integer contractInstanceId) {
        this.contractInstanceId = contractInstanceId;
        return this;
    }

    public String getCurrentStepName() {
        return currentStepName;
    }

    public WorkflowInstanceStartedEvent currentStepName(String currentStepName) {
        this.currentStepName = currentStepName;
        return this;
    }

    public String getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public WorkflowInstanceStartedEvent initiateContractEntry(String initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public String getInitiateContractProcessingEntry() {
        return initiateContractProcessingEntry;
    }

    public WorkflowInstanceStartedEvent initiateContractProcessingEntry(String initiateContractProcessingEntry) {
        this.initiateContractProcessingEntry = initiateContractProcessingEntry;
        return this;
    }
}
