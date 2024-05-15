package com.vsb.kru13.osmzhttpserver.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SimpleHttpParser {

    public static HttpRequest parseRequest(InputStream inputStream) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        // Parse the request line
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("Empty request.");
        }
        String[] requestParts = requestLine.split(" ");
        if (requestParts.length != 3) {
            throw new IOException("Invalid request line.");
        }
        final HttpRequest request = new HttpRequest(requestParts[0], requestParts[1], requestParts[2]);

        // Parse headers
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            String[] headerParts = headerLine.split(": ");
            if (headerParts.length != 2) {
                throw new IOException("Invalid header line: " + headerLine);
            }
            request.addHeader(headerParts[0], headerParts[1]);
        }

        // Body parsing is omitted for simplicity; it could be added here based on "Content-Length" or chunked transfer encoding.

        return request;
    }
}