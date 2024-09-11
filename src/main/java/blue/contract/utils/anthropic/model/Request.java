package blue.contract.utils.anthropic.model;

import java.util.List;

public class Request {
    private String model;
    private int max_tokens;
    private List<Message> messages;
    private String system;
    private double temperature;

    public String getModel() {
        return model;
    }

    public Request model(String model) {
        this.model = model;
        return this;
    }

    public int getMax_tokens() {
        return max_tokens;
    }

    public Request max_tokens(int max_tokens) {
        this.max_tokens = max_tokens;
        return this;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public Request messages(List<Message> messages) {
        this.messages = messages;
        return this;
    }

    public String getSystem() {
        return system;
    }

    public Request system(String system) {
        this.system = system;
        return this;
    }

    public double getTemperature() {
        return temperature;
    }

    public Request temperature(double temperature) {
        this.temperature = temperature;
        return this;
    }
}