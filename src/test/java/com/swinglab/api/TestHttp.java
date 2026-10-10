package com.swinglab.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/** Sends real HTTP requests to the API under test and parses JSON into maps, lists and scalars. */
final class TestHttp {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Map<String, String> JSON_HEADERS = Map.of("Content-Type", "application/json");

    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl;

    TestHttp(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    HttpResponse<String> send(String method, String path, Map<String, String> headers, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        headers.forEach(request::header);
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        try {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        }
    }

    HttpResponse<String> get(String path, Map<String, String> headers) {
        return send("GET", path, headers, null);
    }

    HttpResponse<String> postJson(String path, Map<String, String> headers, String body) {
        Map<String, String> all = new java.util.HashMap<>(headers);
        all.putAll(JSON_HEADERS);
        return send("POST", path, all, body);
    }

    static Object parse(String json) {
        return JSON.readValue(json, Object.class);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(HttpResponse<String> response) {
        return (Map<String, Object>) parse(response.body());
    }
}
