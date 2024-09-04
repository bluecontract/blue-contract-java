package blue.contract.simulator.processor;

import blue.contract.model.blink.APIRequest;
import blue.contract.model.blink.APIResponse;
import blue.contract.simulator.AssistantProcessor;
import blue.language.Blue;
import blue.language.utils.Nodes;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class APIRequestProcessor implements AssistantProcessor<APIRequest, APIResponse> {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    @Override
    public APIResponse process(APIRequest apiRequest, Blue blue) {
        try {
            URI uri = buildUri(apiRequest);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);

            if (apiRequest.getHeaders() != null) {
                for (Map.Entry<String, String> entry : apiRequest.getHeaders().entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            requestBuilder = buildRequestWithBody(apiRequest, blue, requestBuilder);

            if (apiRequest.getTimeout() != null) {
                requestBuilder.timeout(Duration.ofMillis(apiRequest.getTimeout()));
            }

            HttpRequest request = requestBuilder.build();
            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long endTime = System.currentTimeMillis();

            return buildAPIResponse(response, endTime - startTime, blue);
        } catch (IOException | InterruptedException e) {
            return new APIResponse()
                    .statusCode(500)
                    .error("Error processing request: " + e.getMessage());
        }
    }

    private static URI buildUri(APIRequest apiRequest) {
        try {
            StringBuilder uriBuilder = new StringBuilder(apiRequest.getUrl());
            if (apiRequest.getQueryParams() != null && !apiRequest.getQueryParams().isEmpty()) {
                uriBuilder.append("?");
                String queryString = apiRequest.getQueryParams().entrySet().stream()
                        .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" +
                                      URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
                uriBuilder.append(queryString);
            }
            return new URI(uriBuilder.toString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + e.getMessage(), e);
        }
    }

    private static HttpRequest.Builder buildRequestWithBody(APIRequest apiRequest, Blue blue, HttpRequest.Builder requestBuilder) {
        if (apiRequest.getBody() == null) {
            return requestBuilder;
        }

        String contentType = "application/json";
        if (apiRequest.getHeaders() != null && apiRequest.getHeaders().containsKey("Content-Type")) {
            contentType = apiRequest.getHeaders().get("Content-Type");
        } else {
            if (apiRequest.getHeaders() == null) {
                apiRequest.headers(new HashMap<>());
            }
            apiRequest.getHeaders().put("Content-Type", contentType);
        }

        String bodyContent = contentType.startsWith("application/json") ?
                blue.nodeToSimpleJson(apiRequest.getBody()) :
                (String) apiRequest.getBody().getValue();

        return requestBuilder
                .header("Content-Type", contentType)
                .method(apiRequest.getMethod(), HttpRequest.BodyPublishers.ofString(bodyContent));
    }

    private static APIResponse buildAPIResponse(HttpResponse<String> httpResponse, long responseTime, Blue blue) {
        APIResponse apiResponse = new APIResponse()
                .statusCode(httpResponse.statusCode())
                .headers(convertHeaders(httpResponse.headers().map()))
                .responseTime((int) responseTime)
                .contentType(httpResponse.headers().firstValue("Content-Type").orElse("text/plain"));

        String responseBody = httpResponse.body();
        apiResponse.body(apiResponse.getContentType().startsWith("application/json") ?
                blue.jsonToNode(responseBody) :
                Nodes.textNode(responseBody));

        return apiResponse;
    }

    private static Map<String, String> convertHeaders(Map<String, List<String>> multiValueHeaders) {
        return multiValueHeaders.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(", ", entry.getValue())
                ));
    }
}