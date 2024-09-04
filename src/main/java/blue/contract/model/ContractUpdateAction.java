package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;

import java.util.List;


public class ContractUpdateAction {
    private ContractInstance contractInstance;
    @BlueId
    private String contractInstancePrev;
    private Integer epoch;
    private List<Node> emittedEvents;
    @BlueId
    private String initiateContractEntry;
    @BlueId
    private String initiateContractProcessingEntry;
    private Node incomingEvent;

    public ContractUpdateAction() {
    }

    public ContractUpdateAction(ContractInstance contractInstance, String contractInstancePrev, List<Node> emittedEvents,
                                String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        this.contractInstance = contractInstance;
        this.contractInstancePrev = contractInstancePrev;
        this.emittedEvents = emittedEvents;
    }

    public ContractInstance getContractInstance() {
        return contractInstance;
    }

    public String getContractInstancePrev() {
        return contractInstancePrev;
    }

    public int getEpoch() {
        return epoch;
    }

    public List<Node> getEmittedEvents() {
        return emittedEvents;
    }

    public String getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public String getInitiateContractProcessingEntry() {
        return initiateContractProcessingEntry;
    }

    public Node getIncomingEvent() {
        return incomingEvent;
    }

    public ContractUpdateAction contractInstance(ContractInstance contractInstance) {
        this.contractInstance = contractInstance;
        return this;
    }

    public ContractUpdateAction contractInstancePrev(String contractInstancePrev) {
        this.contractInstancePrev = contractInstancePrev;
        return this;
    }

    public ContractUpdateAction epoch(int epoch) {
        this.epoch = epoch;
        return this;
    }

    public ContractUpdateAction emittedEvents(List<Node> emittedEvents) {
        this.emittedEvents = emittedEvents;
        return this;
    }

    public ContractUpdateAction initiateContractEntry(String initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public ContractUpdateAction initiateContractProcessingEntry(String initiateContractProcessingEntry) {
        this.initiateContractProcessingEntry = initiateContractProcessingEntry;
        return this;
    }

    public ContractUpdateAction incomingEvent(Node incomingEvent) {
        this.incomingEvent = incomingEvent;
        return this;
    }
}
