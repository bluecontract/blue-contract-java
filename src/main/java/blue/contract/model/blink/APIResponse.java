package blue.contract.model.blink;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Blink", defaultValueRepositoryKey = "API Response")
public class APIResponse {
    private Integer statusCode;
    private Map<String, String> headers;
    private Node body;
    private String contentType;
    private Integer responseTime;
    private String error;

    public Integer getStatusCode() {
        return statusCode;
    }

    public APIResponse statusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public APIResponse headers(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public Node getBody() {
        return body;
    }

    public APIResponse body(Node body) {
        this.body = body;
        return this;
    }

    public String getContentType() {
        return contentType;
    }

    public APIResponse contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public Integer getResponseTime() {
        return responseTime;
    }

    public APIResponse responseTime(Integer responseTime) {
        this.responseTime = responseTime;
        return this;
    }

    public String getError() {
        return error;
    }

    public APIResponse error(String error) {
        this.error = error;
        return this;
    }
}