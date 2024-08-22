package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class ExternalContract {
    private Node initiateContractEntry;
    private Node localContractInstanceId;

    public Node getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public Node getLocalContractInstanceId() {
        return localContractInstanceId;
    }

    public ExternalContract initiateContractEntry(Node initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public ExternalContract localContractInstanceId(Node localContractInstanceId) {
        this.localContractInstanceId = localContractInstanceId;
        return this;
    }
}
