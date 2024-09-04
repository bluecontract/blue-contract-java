package blue.contract.model.action;

import blue.contract.model.Contract;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class InitiateContractAction {
    private Contract contract;

    public InitiateContractAction() {
    }

    public InitiateContractAction(Contract contract) {
        this.contract = contract;
    }

    public Contract getContract() {
        return contract;
    }

    public InitiateContractAction contract(Contract contract) {
        this.contract = contract;
        return this;
    }
}
