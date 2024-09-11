package blue.contract.model.blink;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

@TypeBlueId(defaultValueRepositoryDir = "Blink", defaultValueRepositoryKey = "LLM Response")
public class LLMResponse {
    private Node content;
    private Integer responseTime;

    public Node getContent() {
        return content;
    }

    public LLMResponse content(Node content) {
        this.content = content;
        return this;
    }

    public Integer getResponseTime() {
        return responseTime;
    }

    public LLMResponse responseTime(Integer responseTime) {
        this.responseTime = responseTime;
        return this;
    }
}