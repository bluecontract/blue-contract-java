package blue.contract.model.blink;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Blink", defaultValueRepositoryKey = "LLM Request")
public class LLMRequest {
    private String prompt;
    private Map<String, Node> promptParams;

    public String getPrompt() {
        return prompt;
    }

    public LLMRequest prompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    public Map<String, Node> getPromptParams() {
        return promptParams;
    }

    public LLMRequest promptParams(Map<String, Node> promptParams) {
        this.promptParams = promptParams;
        return this;
    }
}