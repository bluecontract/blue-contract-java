package blue.contract.model.blink;

import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class MainChatMessage extends ConversationEntry {
    private String responseTo;
    private String messageType;
    private String message;
    private String userInput;
    private String userInputDetails;

    public String getResponseTo() {
        return responseTo;
    }

    public MainChatMessage responseTo(String responseTo) {
        this.responseTo = responseTo;
        return this;
    }

    public String getMessageType() {
        return messageType;
    }

    public MainChatMessage messageType(String messageType) {
        this.messageType = messageType;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public MainChatMessage message(String message) {
        this.message = message;
        return this;
    }

    public String getUserInput() {
        return userInput;
    }

    public MainChatMessage userInput(String userInput) {
        this.userInput = userInput;
        return this;
    }

    public String getUserInputDetails() {
        return userInputDetails;
    }

    public MainChatMessage userInputDetails(String userInputDetails) {
        this.userInputDetails = userInputDetails;
        return this;
    }
}
