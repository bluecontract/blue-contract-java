package blue.contract.model.blink;

import blue.contract.model.TimelineEntry;
import blue.language.model.Node;
import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class RecommendedUserActionMessage extends ConversationEntry {
    private String message;
    private Node action;

    public String getMessage() {
        return message;
    }

    public RecommendedUserActionMessage message(String message) {
        this.message = message;
        return this;
    }

    public Node getAction() {
        return action;
    }

    public RecommendedUserActionMessage action(Node action) {
        this.action = action;
        return this;
    }
}
