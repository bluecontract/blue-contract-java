package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;

import java.util.List;


public class ContractUpdate {
    private ContractInstance contractInstance;
    private Node contractInstancePrev;
    private List<Node> emittedEvents;

    public ContractUpdate() {
    }

    public ContractUpdate(ContractInstance contractInstance, Node contractInstancePrev, List<Node> emittedEvents) {
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

    public List<Node> getEmittedEvents() {
        return emittedEvents;
    }

    public ContractUpdate contractInstance(ContractInstance contractInstance) {
        this.contractInstance = contractInstance;
        return this;
    }

    public ContractUpdate contractInstancePrev(Node contractInstancePrev) {
        this.contractInstancePrev = contractInstancePrev;
        return this;
    }

    public ContractUpdate emittedEvents(List<Node> emittedEvents) {
        this.emittedEvents = emittedEvents;
        return this;
    }
}
