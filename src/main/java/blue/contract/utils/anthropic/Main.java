package blue.contract.utils.anthropic;

import blue.contract.utils.anthropic.model.Message;
import blue.contract.utils.anthropic.model.Request;
import blue.contract.utils.anthropic.model.Response;
import blue.contract.utils.anthropic.model.Content;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            // Create AnthropicConfig
            AnthropicConfig config = new AnthropicConfig(AnthropicKey.ANTHROPIC_KEY)
                    .maxTokens(8000)
                    .model("claude-3-5-sonnet-20240620");

            // Create Anthropic instance
            Anthropic anthropic = new Anthropic(config);

            // Create a list of messages
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("user", "Hello, can you tell me about the Java programming language?"));

            // Create the request
            Request request = new Request()
                    .messages(messages)
                    .system("You're a joke maker, wrapping each joke into JSON")
                    .temperature(0.0);

            // Send the request
            Response response = anthropic.sendRequest(request);

            // Process the response
            if (response != null && response.getContent() != null) {
                for (Content content : response.getContent()) {
                    if ("text".equals(content.getType())) {
                        System.out.println("AI Response: " + content.getText());
                    }
                }
            }

            // Print usage information
            if (response != null && response.getUsage() != null) {
                System.out.println("Input tokens: " + response.getUsage().getInput_tokens());
                System.out.println("Output tokens: " + response.getUsage().getOutput_tokens());
            }

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}