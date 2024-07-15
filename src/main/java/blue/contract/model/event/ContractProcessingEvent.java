package blue.contract.model.event;

import blue.language.model.Node;

public class ContractProcessingEvent {
    private Integer contractInstance;
    private Integer workflowInstance;
    private Node initiateContractEntry;
    private Node event;

    public ContractProcessingEvent() {
    }

    public ContractProcessingEvent(Integer contractInstance, Integer workflowInstance, Node initiateContractEntry, Node event) {
        this.contractInstance = contractInstance;
        this.workflowInstance = workflowInstance;
        this.initiateContractEntry = initiateContractEntry;
        this.event = event;
    }

    public Integer getContractInstance() {
        return contractInstance;
    }

    public Integer getWorkflowInstance() {
        return workflowInstance;
    }

    public Node getInitiateContractEntry() {
        return initiateContractEntry;
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

    public ContractProcessingEvent initiateContractEntry(Node initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public ContractProcessingEvent event(Node event) {
        this.event = event;
        return this;
    }

}