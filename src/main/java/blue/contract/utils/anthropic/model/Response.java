package blue.contract.utils.anthropic.model;

import java.util.List;

public class Response {
    private String id;
    private String type;
    private String role;
    private List<Content> content;
    private String model;
    private String stop_reason;
    private String stop_sequence;
    private Usage usage;

    public String getId() {
        return id;
    }

    public Response id(String id) {
        this.id = id;
        return this;
    }

    public String getType() {
        return type;
    }

    public Response type(String type) {
        this.type = type;
        return this;
    }

    public String getRole() {
        return role;
    }

    public Response role(String role) {
        this.role = role;
        return this;
    }

    public List<Content> getContent() {
        return content;
    }

    public Response content(List<Content> content) {
        this.content = content;
        return this;
    }

    public String getModel() {
        return model;
    }

    public Response model(String model) {
        this.model = model;
        return this;
    }

    public String getStop_reason() {
        return stop_reason;
    }

    public Response stop_reason(String stop_reason) {
        this.stop_reason = stop_reason;
        return this;
    }

    public String getStop_sequence() {
        return stop_sequence;
    }

    public Response stop_sequence(String stop_sequence) {
        this.stop_sequence = stop_sequence;
        return this;
    }

    public Usage getUsage() {
        return usage;
    }

    public Response usage(Usage usage) {
        this.usage = usage;
        return this;
    }
}
