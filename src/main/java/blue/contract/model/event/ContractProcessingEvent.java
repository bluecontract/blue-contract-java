package blue.contract.model.event;

import blue.language.model.BlueId;
import blue.language.model.Node;

@BlueId("HQgee2RezD7yupwCgRnSzKwhqUGwzNNRq1n1eyFD9X1w")
public class ContractProcessingEvent {
    private Integer contractInstanceId;
    private Integer workflowInstanceId;
    private String workflowStepName;
    private Node initiateContractEntry;
    private Node initiateContractProcessingEntry;
    private Node event;

    public ContractProcessingEvent() {
    }

    public Integer getContractInstanceId() {
        return contractInstanceId;
    }

    public Integer getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public String getWorkflowStepName() {
        return workflowStepName;
    }

    public Node getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public Node getInitiateContractProcessingEntry() {
        return initiateContractProcessingEntry;
    }

    public Node getEvent() {
        return event;
    }

    public ContractProcessingEvent contractInstanceId(Integer contractInstanceId) {
        this.contractInstanceId = contractInstanceId;
        return this;
    }

    public ContractProcessingEvent workflowInstanceId(Integer workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
        return this;
    }

    public ContractProcessingEvent workflowStepName(String workflowStepName) {
        this.workflowStepName = workflowStepName;
        return this;
    }

    public ContractProcessingEvent initiateContractEntry(Node initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public ContractProcessingEvent initiateContractEntry(String initiateContractEntryBlueId) {
        this.initiateContractEntry = new Node().blueId(initiateContractEntryBlueId);
        return this;
    }

    public ContractProcessingEvent initiateContractProcessingEntry(Node initiateContractProcessingEntry) {
        this.initiateContractProcessingEntry = initiateContractProcessingEntry;
        return this;
    }

    public ContractProcessingEvent initiateContractProcessingEntry(String initiateContractProcessingEntryBlueId) {
        this.initiateContractProcessingEntry = new Node().blueId(initiateContractProcessingEntryBlueId);
        return this;
    }

    public ContractProcessingEvent event(Node event) {
        this.event = event;
        return this;
    }

}