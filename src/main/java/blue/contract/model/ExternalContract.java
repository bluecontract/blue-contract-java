package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;

@BlueId("83vpYdEkjbguc9Mbixz2qV2ZkzaCgV4gptSeyyU4QzaJ")
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
