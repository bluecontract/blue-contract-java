package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;

import java.util.List;


public class ContractUpdate {
    private ContractInstance contractInstance;
    private Node contractInstancePrev;
    private int epoch;
    private List<Node> emittedEvents;
    private Node initiateContractEntry;
    private Node initiateContractProcessingEntry;

    public ContractUpdate() {
    }

    public ContractUpdate(ContractInstance contractInstance, Node contractInstancePrev, List<Node> emittedEvents,
                          String initiateContractEntryBlueId, String initiateContractProcessingEntryBlueId) {
        this.contractInstance = contractInstance;
        this.contractInstancePrev = contractInstancePrev;
        this.emittedEvents = emittedEvents;
    }

    public ContractInstance getContractInstance() {
        return contractInstance;
    }

    public Node getContractInstancePrev() {
        return contractInstancePrev;
    }

    public int getEpoch() {
        return epoch;
    }

    public List<Node> getEmittedEvents() {
        return emittedEvents;
    }

    public Node getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public Node getInitiateContractProcessingEntry() {
        return initiateContractProcessingEntry;
    }

    public ContractUpdate contractInstance(ContractInstance contractInstance) {
        this.contractInstance = contractInstance;
        return this;
    }

    public ContractUpdate contractInstancePrev(Node contractInstancePrev) {
        this.contractInstancePrev = contractInstancePrev;
        return this;
    }

    public ContractUpdate epoch(int epoch) {
        this.epoch = epoch;
        return this;
    }

    public ContractUpdate emittedEvents(List<Node> emittedEvents) {
        this.emittedEvents = emittedEvents;
        return this;
    }

    public ContractUpdate initiateContractEntry(Node initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public ContractUpdate initiateContractProcessingEntry(Node initiateContractProcessingEntry) {
        this.initiateContractProcessingEntry = initiateContractProcessingEntry;
        return this;
    }
}
