package blue.contract.utils.anthropic.model;

public class Message {
    private String role;
    private String content;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public Message role(String role) {
        this.role = role;
        return this;
    }

    public String getContent() {
        return content;
    }

    public Message content(String content) {
        this.content = content;
        return this;
    }
}
