package blue.contract.model.event;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class ContractUpdateEvent {
    private Node changeset;

    public Node getChangeset() {
        return changeset;
    }

    public ContractUpdateEvent changeset(Node changeset) {
        this.changeset = changeset;
        return this;
    }
}