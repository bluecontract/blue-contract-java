package blue.contract.model.blink;

import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class UserMessage extends ConversationEntry {
    private String message;

    public String getMessage() {
        return message;
    }

    public UserMessage message(String message) {
        this.message = message;
        return this;
    }
}
