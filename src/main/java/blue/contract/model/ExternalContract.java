package blue.contract.model;

import blue.language.model.BlueId;
import blue.language.model.Node;

@BlueId("BbNPrYyYhcqMxj24LAzrnYHFgYxkruihY76QuypYxuYW")
public class ExternalContract {
    private Node initiateContractEntry;
    private Integer localContractInstanceId;

    public Node getInitiateContractEntry() {
        return initiateContractEntry;
    }

    public Integer getLocalContractInstanceId() {
        return localContractInstanceId;
    }

    public ExternalContract initiateContractEntry(Node initiateContractEntry) {
        this.initiateContractEntry = initiateContractEntry;
        return this;
    }

    public ExternalContract localContractInstanceId(Integer localContractInstanceId) {
        this.localContractInstanceId = localContractInstanceId;
        return this;
    }
}
