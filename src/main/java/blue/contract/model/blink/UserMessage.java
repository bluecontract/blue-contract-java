package blue.contract.model.blink;

import blue.language.model.TypeBlueId;

import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Blink")
public class UserMessage extends ConversationEntry {
    private String message;
    private Map<String, String> contracts;
    private Boolean generateResponse;

    public String getMessage() {
        return message;
    }

    public UserMessage message(String message) {
        this.message = message;
        return this;
    }

    public Map<String, String> getContracts() {
        return contracts;
    }

    public UserMessage contracts(Map<String, String> contracts) {
        this.contracts = contracts;
        return this;
    }

    public Boolean getGenerateResponse() {
        return generateResponse;
    }

    public UserMessage generateResponse(Boolean generateResponse) {
        this.generateResponse = generateResponse;
        return this;
    }
}
