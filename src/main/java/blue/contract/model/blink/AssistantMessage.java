package blue.contract.model.blink;

import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class AssistantMessage extends ConversationEntry {
    private String message;
    private String priority;

    public String getMessage() {
        return message;
    }

    public AssistantMessage message(String message) {
        this.message = message;
        return this;
    }

    public String getPriority() {
        return priority;
    }

    public AssistantMessage priority(String priority) {
        this.priority = priority;
        return this;
    }
}
