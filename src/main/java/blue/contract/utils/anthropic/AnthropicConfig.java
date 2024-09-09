package blue.contract.utils.anthropic;

public class AnthropicConfig {
    private String apiKey;
    private int maxTokens;
    private String model;

    public AnthropicConfig(String apiKey) {
        this.apiKey = apiKey;
    }

    public AnthropicConfig(String apiKey, int maxTokens, String model) {
        this.apiKey = apiKey;
        this.maxTokens = maxTokens;
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public AnthropicConfig apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public AnthropicConfig maxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public String getModel() {
        return model;
    }

    public AnthropicConfig model(String model) {
        this.model = model;
        return this;
    }
}