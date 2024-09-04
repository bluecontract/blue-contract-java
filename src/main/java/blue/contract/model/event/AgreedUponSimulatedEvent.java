package blue.contract.model.event;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class AgreedUponSimulatedEvent {
    private Node event;

    public Node getEvent() {
        return event;
    }

    public AgreedUponSimulatedEvent event(Node event) {
        this.event = event;
        return this;
    }
}