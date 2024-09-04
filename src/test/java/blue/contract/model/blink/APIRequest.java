package blue.contract.model.blink;

import blue.language.model.Node;
import blue.language.model.TypeBlueId;

import java.util.Map;

@TypeBlueId(defaultValueRepositoryDir = "Blink", defaultValueRepositoryKey = "API Request")
public class APIRequest {
    private String method;
    private String url;
    private Map<String, String> headers;
    private Map<String, Object> queryParams;
    private Node body;
    private Integer timeout;

    public String getMethod() {
        return method;
    }

    public APIRequest method(String method) {
        this.method = method;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public APIRequest url(String url) {
        this.url = url;
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public APIRequest headers(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public Map<String, Object> getQueryParams() {
        return queryParams;
    }

    public APIRequest queryParams(Map<String, Object> queryParams) {
        this.queryParams = queryParams;
        return this;
    }

    public Node getBody() {
        return body;
    }

    public APIRequest body(Node body) {
        this.body = body;
        return this;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public APIRequest timeout(Integer timeout) {
        this.timeout = timeout;
        return this;
    }
}