package blue.contract.utils.anthropic;

import blue.contract.utils.anthropic.model.Request;
import blue.contract.utils.anthropic.model.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Anthropic {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_KEY = "hidden";
    private static final String API_VERSION = "2023-06-01";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Response sendRequest(Request request) throws Exception {
        String requestBody = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", API_KEY)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API request failed with status code: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), Response.class);
    }
}