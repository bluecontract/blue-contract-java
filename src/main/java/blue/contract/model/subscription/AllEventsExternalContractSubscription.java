package blue.contract.model.subscription;

import blue.language.model.BlueId;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class AllEventsExternalContractSubscription extends ContractSubscription {
    @BlueId
    private String initiateContractEntry;

    public AllEventsExternalContractSubscription() {
    }

    public AllEventsExternalContractSubscription(String initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
    }

    public String getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public AllEventsExternalContractSubscription initiateContractEntry(String initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }
}