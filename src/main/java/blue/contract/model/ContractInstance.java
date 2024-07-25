package blue.contract.model;

import blue.language.model.Node;

import java.util.List;
import java.util.stream.Collectors;

public class ContractInstance implements Cloneable {

    public static final int ROOT_INSTANCE_ID = 0;

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

    @Override
    public ContractInstance clone() {
        try {
            ContractInstance cloned = (ContractInstance) super.clone();
            cloned.contractState = this.contractState != null ? this.contractState.clone() : null;
            cloned.processingState = this.processingState != null ? this.processingState.clone() : null;
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("ContractInstance should be cloneable", e);
        }
    }
}