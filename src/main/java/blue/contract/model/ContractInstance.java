package blue.contract.model;

public class ContractInstance {

    private int id;
    private GenericContract contractState;
    private ProcessingState processingState;

    public int getId() {
        return id;
    }

    public GenericContract getContractState() {
        return contractState;
    }

    public ProcessingState getProcessingState() {
        return processingState;
    }

    public ContractInstance id(int id) {
        this.id = id;
        return this;
    }

    public ContractInstance contractState(GenericContract contractState) {
        this.contractState = contractState;
        return this;
    }

    public ContractInstance processingState(ProcessingState processingState) {
        this.processingState = processingState;
        return this;
    }

}