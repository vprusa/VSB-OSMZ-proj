package com.vsb.kru13.osmzhttpserver.http;

import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class HttpResponse {
    private String version = "HTTP/1.1"; // Default to HTTP/1.1
    private int statusCode;
    private String reasonPhrase;
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";

    public HttpResponse(int statusCode, String reasonPhrase) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    public HttpResponse(
            int statusCode,
            String reasonPhrase,
            String body
    ) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.body = body;
    }

    public HttpResponse(
            int statusCode,
            String reasonPhrase,
            String body,
            Map<String, String> headers
    ) {
        this(statusCode, reasonPhrase, body);
        this.headers.putAll(headers);
    }


    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion(){
        return version;
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public void setBody(String body) {
        this.body = body;
        this.addHeader("Content-Length", String.valueOf(body.length()));
    }

    public void setBody(BufferedReader bufferedReader) {
        this.body = body;
        this.addHeader("Content-Length", String.valueOf(body.length()));
    }

    @Override
    public String toString() {
        StringBuilder response = new StringBuilder();
        // Status line
        response.append(version).append(" ").append(statusCode).append(" ").append(reasonPhrase).append("\r\n");
        // Headers
        Set<Map.Entry<String, String>> headerEntries = headers.entrySet();
        for (Map.Entry<String, String> entry : headerEntries) {
            response.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        // Separate headers from the body
        response.append("\r\n");
        // Body
        response.append(body);
        return response.toString();
    }

}