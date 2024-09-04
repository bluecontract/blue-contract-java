package blue.contract.model.step;

import blue.contract.model.Contract;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class InitializeLocalContractStep extends WorkflowStep {
    private Node contract;

    public Node getContract() {
        return contract;
    }

    public InitializeLocalContractStep contract(Node contract) {
        this.contract = contract;
        return this;
    }
}
