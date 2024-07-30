package blue.contract.model.event;

import blue.language.model.BlueId;
import blue.language.model.Node;

@BlueId({"DgQKBxaG8m5xfuZSQDm3yJqGZusU8k7tuizzQgg2XMa5"})
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