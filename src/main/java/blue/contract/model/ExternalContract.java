package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class ExternalContract {
    @BlueId
    private String initiateContractEntry;
    private Integer localContractInstanceId;

    public String getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public Integer getLocalContractInstanceId() {
        return localContractInstanceId;
    }

    public ExternalContract initiateContractEntry(String initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public ExternalContract localContractInstanceId(Integer localContractInstanceId) {
        this.localContractInstanceId = localContractInstanceId;
        return this;
    }
}
