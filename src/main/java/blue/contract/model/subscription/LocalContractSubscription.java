package blue.contract.model.subscription;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class LocalContractSubscription extends ContractSubscription {
    private Integer contractInstanceId;
    private Integer workflowInstanceId;
    private Node event;

    public Integer getContractInstanceId() {
        return contractInstanceId;
    }

    public LocalContractSubscription contractInstanceId(Integer contractInstanceId) {
        this.contractInstanceId = contractInstanceId;
        return this;
    }

    public Integer getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public LocalContractSubscription workflowInstanceId(Integer workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
        return this;
    }

    public Node getEvent() {
        return event;
    }

    public LocalContractSubscription event(Node event) {
        this.event = event;
        return this;
    }
}