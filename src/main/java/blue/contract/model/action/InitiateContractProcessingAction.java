package blue.contract.model.action;

import blue.contract.model.Contract;
import blue.language.model.BlueId;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class InitiateContractProcessingAction {
    @BlueId
    private String initiateContractEntry;
    private Contract contract;

    public InitiateContractProcessingAction() {
    }

    public InitiateContractProcessingAction(String initiateContractEntry, Contract contract) {
        this.initiateContractEntry = initiateContractEntry;
        this.contract = contract;
    }

    public String getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public InitiateContractProcessingAction initiateContractEntry(String initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public Contract getContract() {
        return contract;
    }

    public InitiateContractProcessingAction contract(Contract contract) {
        this.contract = contract;
        return this;
    }
}
