package blue.contract.model;

import blue.language.model.Node;

public class ContractInstance {

    private int id;
    private Node contractState;
    private ProcessingState processingState;

    public int getId() {
        return id;
    }

    public Node getContractState() {
        return contractState;
    }

    public ProcessingState getProcessingState() {
        return processingState;
    }

    public ContractInstance id(int id) {
        this.id = id;
        return this;
    }

    public ContractInstance contractState(Node contractState) {
        this.contractState = contractState;
        return this;
    }

    public ContractInstance processingState(ProcessingState processingState) {
        this.processingState = processingState;
        return this;
    }

}