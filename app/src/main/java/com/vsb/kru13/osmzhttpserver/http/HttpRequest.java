package com.vsb.kru13.osmzhttpserver.http;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private String method;
    private String uri;
    private String version;
    private Map<String, String> headers = new HashMap<>();

    // Constructor
    public HttpRequest(String method, String uri, String version) {
        this.method = method;
        this.uri = uri;
        this.version = version;
    }

    // Getters and Setters
    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    // A simple representation for debugging
    @Override
    public String toString() {
        return "HttpRequest{" +
                "method='" + method + '\'' +
                ", uri='" + uri + '\'' +
                ", version='" + version + '\'' +
                ", headers=" + headers +
                '}';
    }
}