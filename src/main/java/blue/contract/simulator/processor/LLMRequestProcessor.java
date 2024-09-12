package blue.contract.simulator.processor;

import blue.contract.simulator.AssistantProcessor;
import blue.contract.model.blink.LLMRequest;
import blue.contract.model.blink.LLMResponse;
import blue.contract.utils.anthropic.Anthropic;
import blue.contract.utils.anthropic.AnthropicConfig;
import blue.contract.utils.anthropic.model.Content;
import blue.contract.utils.anthropic.model.Message;
import blue.contract.utils.anthropic.model.Request;
import blue.contract.utils.anthropic.model.Response;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.utils.Properties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static blue.contract.utils.anthropic.AnthropicKey.ANTHROPIC_KEY;
import static blue.language.utils.Properties.TEXT_TYPE_BLUE_ID;

public class LLMRequestProcessor implements AssistantProcessor<LLMRequest, LLMResponse> {

    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-20240620";
    private static final int DEFAULT_MAX_TOKENS = 2048;

    @Override
    public LLMResponse process(LLMRequest llmRequest, Blue blue) {
        System.out.println("Processing LLM request");
        try {
            long startTime = System.currentTimeMillis();

            Request anthropicRequest = new Request()
                    .model(DEFAULT_MODEL)
                    .max_tokens(DEFAULT_MAX_TOKENS);

            List<Message> messages = new ArrayList<>();
            messages.add(new Message("user", buildPrompt(llmRequest, blue)));
            anthropicRequest.messages(messages);
            anthropicRequest.system("Process this task according to instructions.");
            anthropicRequest.temperature(0.0);

            Response anthropicResponse = new Anthropic(new AnthropicConfig(ANTHROPIC_KEY)).sendRequest(anthropicRequest);

            long endTime = System.currentTimeMillis();
            int responseTime = (int) (endTime - startTime);

            return buildLLMResponse(anthropicResponse, responseTime, blue);
        } catch (Exception e) {
            System.out.println("LLM error:");
            System.out.println(e.getMessage());
            return new LLMResponse()
                    .content(new Node().value("Error when processing Anthropic request: " + e.getMessage()).type(
                            new Node().blueId(TEXT_TYPE_BLUE_ID)
                    ))
                    .responseTime(-1);
        }
    }

    private String buildPrompt(LLMRequest llmRequest, Blue blue) {
        StringBuilder promptBuilder = new StringBuilder(llmRequest.getPrompt());

        if (llmRequest.getPromptParams() != null && !llmRequest.getPromptParams().isEmpty()) {
            promptBuilder.append("\n\nContext:");
            promptBuilder.append("\n").append(blue.objectToSimpleYaml(llmRequest.getPromptParams()));
        }

        return promptBuilder.toString();
    }

    private LLMResponse buildLLMResponse(Response anthropicResponse, int responseTime, Blue blue) {
        LLMResponse llmResponse = new LLMResponse()
                .responseTime(responseTime);
        ObjectMapper objectMapper = new ObjectMapper();

        if (anthropicResponse.getContent() != null && !anthropicResponse.getContent().isEmpty()) {
            Content content = anthropicResponse.getContent().get(0);
            if (content != null && content.getText() != null) {
                String text = content.getText();
                try {
                    objectMapper.readTree(text);
                    llmResponse.content(blue.jsonToNode(content.getText()));
                } catch (Exception e) {
                    llmResponse.content(new Node().value(text).type(new Node().blueId(TEXT_TYPE_BLUE_ID)));
                }
            }
        }

        return llmResponse;
    }
}