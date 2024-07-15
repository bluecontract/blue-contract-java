package blue.contract.model.event;

import blue.language.model.BlueId;
import blue.language.model.Node;

@BlueId("GRb1M7WkXw1rWsXprQLpd9MbhDJa39bDasninVUyuTMR")
public class ContractProcessingEvent {
    private Integer contractInstance;
    private Integer workflowInstance;
    private String workflowStepName;
    private Node initiateContractEntry;
    private Node initiateContractProcessingEntry;
    private Node event;

    public ContractProcessingEvent() {
    }

    public Integer getContractInstance() {
        return contractInstance;
    }

    public Integer getWorkflowInstance() {
        return workflowInstance;
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

    public ContractProcessingEvent contractInstance(Integer contractInstance) {
        this.contractInstance = contractInstance;
        return this;
    }

    public ContractProcessingEvent workflowInstance(Integer workflowInstance) {
        this.workflowInstance = workflowInstance;
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