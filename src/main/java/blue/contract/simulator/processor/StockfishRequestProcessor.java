package blue.contract.simulator.processor;

import blue.contract.model.blink.StockfishRequest;
import blue.contract.model.blink.StockfishResponse;
import blue.contract.simulator.AssistantProcessor;
import blue.language.Blue;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class StockfishRequestProcessor implements AssistantProcessor<StockfishRequest, StockfishResponse> {

    private static final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public StockfishResponse process(StockfishRequest request, Blue blue) {
        String url = "https://chess-api.com/v1";
        Map<String, Object> requestBody = Map.of(
                "fen", request.getFen(),
                "depth", request.getDepth()
        );

        try {
            String response = sendPostRequest(url, requestBody);
            return mapResponseToStockfishResponse(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String sendPostRequest(String url, Map<String, Object> requestBody) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .uri(new URI(url))
                .header("Content-Type", "application/json")
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static StockfishResponse mapResponseToStockfishResponse(String jsonResponse) throws Exception {
        Map<String, Object> responseMap = objectMapper.readValue(jsonResponse, Map.class);
        return new StockfishResponse()
                .text((String) responseMap.get("text"))
                .eval(BigDecimal.valueOf(Double.parseDouble(String.valueOf(responseMap.get("eval")))))
                .move((String) responseMap.get("move"))
                .fen((String) responseMap.get("fen"))
                .from((String) responseMap.get("from"))
                .to((String) responseMap.get("to"))
                .depth(Integer.valueOf(String.valueOf(responseMap.get("depth"))))
                .winChance(BigDecimal.valueOf(Double.parseDouble(String.valueOf(responseMap.get("eval")))));
    }
}