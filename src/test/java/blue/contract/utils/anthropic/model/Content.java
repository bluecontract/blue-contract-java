package blue.contract.utils.anthropic.model;

public class Content {
    private String type;
    private String text;

    public String getType() {
        return type;
    }

    public Content type(String type) {
        this.type = type;
        return this;
    }

    public String getText() {
        return text;
    }

    public Content text(String text) {
        this.text = text;
        return this;
    }
}
