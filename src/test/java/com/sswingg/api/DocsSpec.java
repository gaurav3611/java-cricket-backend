package com.sswingg.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads src/main/resources/static/docs/endpoints.json, the list of endpoints and responses that
 * the API reference page at /docs shows. The tests use it to check that the page tells the truth.
 */
final class DocsSpec {

    private final Map<String, Object> root;

    @SuppressWarnings("unchecked")
    private DocsSpec(Object parsed) {
        this.root = (Map<String, Object>) parsed;
    }

    static DocsSpec load() {
        try (InputStream in = DocsSpec.class.getResourceAsStream("/static/docs/endpoints.json")) {
            if (in == null) {
                throw new IllegalStateException("static/docs/endpoints.json is not on the classpath");
            }
            return new DocsSpec(TestHttp.JSON.readValue(in, Object.class));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    Map<String, Object> root() {
        return root;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> endpoints() {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> group : (List<Map<String, Object>>) root.get("groups")) {
            all.addAll((List<Map<String, Object>>) group.get("endpoints"));
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> commonResponses() {
        return (List<Map<String, Object>>) root.get("common");
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> responses(Map<String, Object> endpoint) {
        return (List<Map<String, Object>>) endpoint.get("responses");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> verify(Map<String, Object> response) {
        return (Map<String, Object>) response.get("verify");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> tryRequest(Map<String, Object> endpoint) {
        return (Map<String, Object>) endpoint.get("try");
    }

    /** Keys whose values change on every run, plus the ones this example asks to ignore. */
    @SuppressWarnings("unchecked")
    Set<String> volatileKeys(Map<String, Object> verify) {
        Set<String> keys = new HashSet<>((List<String>) root.get("volatile_keys"));
        if (verify.get("ignore_values") instanceof List<?> extra) {
            keys.addAll((List<String>) extra);
        }
        return keys;
    }

    static String label(Map<String, Object> endpoint, Map<String, Object> response) {
        return endpoint.get("method") + " " + endpoint.get("path") + " -> " + response.get("status")
                + " (" + response.get("when") + ")";
    }
}
