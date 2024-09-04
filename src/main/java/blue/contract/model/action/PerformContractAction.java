package blue.contract.model.action;

import blue.language.model.BlueId;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class PerformContractAction<T> {
    @BlueId
    private String initiateContractEntry;
    private Integer contractInstance;
    private Integer workflowInstance;
    private String workflowName;
    private T action;

    public String getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public PerformContractAction<T> initiateContractEntry(String initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public Integer getContractInstance() {
        return contractInstance;
    }

    public PerformContractAction<T> contractInstance(Integer contractInstance) {
        this.contractInstance = contractInstance;
        return this;
    }

    public Integer getWorkflowInstance() {
        return workflowInstance;
    }

    public PerformContractAction<T> workflowInstance(Integer workflowInstance) {
        this.workflowInstance = workflowInstance;
        return this;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public PerformContractAction<T> workflowName(String workflowName) {
        this.workflowName = workflowName;
        return this;
    }

    public T getAction() {
        return action;
    }

    public PerformContractAction<T> action(T action) {
        this.action = action;
        return this;
    }
}
