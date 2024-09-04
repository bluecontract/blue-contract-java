package blue.contract.model.step;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public class ExpectEventStep extends WorkflowStep {
    private Node event;

    public Node getEvent() {
        return event;
    }

    public ExpectEventStep event(Node event) {
        this.event = event;
        return this;
    }
}
