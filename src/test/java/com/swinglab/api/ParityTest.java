package com.swinglab.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Replays the requests recorded from the Flask backend (parity/golden.json) against this API,
 * in demo mode, over real HTTP, and checks that the answers are the same.
 *
 * golden.json is produced by parity/generate_golden.py. It is the contract for this port.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // Force demo mode even if a real key or a .env file sits in the working directory.
            "swinglab.firebase.service-account-json=",
            "swinglab.firebase.service-account-path=target/no-such-service-account.json",
            // A three-file stand-in for the real frontend, so the test needs no other repository.
            "swinglab.frontend-dir=src/test/resources/frontend-fixture"
        })
@DirtiesContext // the last step starts the demo simulation, so the context must not be reused
class ParityTest {

    private static final double NUMBER_TOLERANCE = 1e-9;

    @Value("${local.server.port}")
    int port;

    @Test
    @SuppressWarnings("unchecked")
    void answersLikeTheFlaskBackend() throws Exception {
        Map<String, Object> golden;
        try (InputStream in = getClass().getResourceAsStream("/parity/golden.json")) {
            golden = (Map<String, Object>) TestHttp.JSON.readValue(in, Object.class);
        }
        Set<String> volatileKeys = new HashSet<>((List<String>) golden.get("volatile_keys"));
        List<Map<String, Object>> steps = (List<Map<String, Object>>) golden.get("steps");
        assertTrue(steps.size() > 300, "golden.json looks incomplete: " + steps.size() + " steps");

        TestHttp http = new TestHttp(port);
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            Map<String, String> headers = (Map<String, String>) step.get("headers");
            HttpResponse<String> response = http.send(
                    (String) step.get("method"), (String) step.get("path"), headers, (String) step.get("body"));

            List<String> diffs = new ArrayList<>();
            int expectedStatus = ((Number) step.get("status")).intValue();
            if (response.statusCode() != expectedStatus) {
                diffs.add("status: expected " + expectedStatus + " but was " + response.statusCode()
                        + " with body " + response.body());
            } else {
                String compare = (String) step.get("compare");
                if (!compare.equals("status")) {
                    String contentType = response.headers().firstValue("Content-Type").orElse("");
                    Object body = null;
                    boolean isJson = contentType.startsWith("application/json");
                    if (isJson) {
                        try {
                            body = TestHttp.parse(response.body());
                        } catch (RuntimeException e) {
                            isJson = false;
                        }
                    }
                    if (!isJson) {
                        diffs.add("expected a JSON body but got Content-Type '" + contentType + "' with body "
                                + response.body());
                    } else if (compare.equals("json")) {
                        compare("$", step.get("json"), body, volatileKeys, diffs);
                    } else if (!(body instanceof Map<?, ?> map) || !(map.get("error") instanceof String)) {
                        diffs.add("expected a body with an \"error\" text but was " + response.body());
                    }
                }
            }
            if (headers.containsKey("Origin")) {
                String allowOrigin = response.headers().firstValue("Access-Control-Allow-Origin").orElse(null);
                if (!Objects.equals(step.get("allow_origin"), allowOrigin)) {
                    diffs.add("Access-Control-Allow-Origin: expected " + step.get("allow_origin")
                            + " but was " + allowOrigin);
                }
            }
            if (!diffs.isEmpty()) {
                failures.add("step " + i + " [" + step.get("name") + "] " + step.get("method") + " "
                        + step.get("path") + "\n    " + String.join("\n    ", diffs));
            }
        }

        assertTrue(failures.isEmpty(), () -> failures.size() + " of " + steps.size()
                + " steps differ from the Flask backend:\n" + String.join("\n", failures.subList(0, Math.min(25, failures.size()))));
    }

    /** Deep comparison. Volatile keys must be present with the same JSON type, their values may differ. */
    private static void compare(String path, Object expected, Object actual, Set<String> volatileKeys, List<String> diffs) {
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            if (!expectedMap.keySet().equals(actualMap.keySet())) {
                diffs.add(path + ": keys expected " + new TreeSet<>(expectedMap.keySet())
                        + " but were " + new TreeSet<>(actualMap.keySet()));
                return;
            }
            for (Object key : expectedMap.keySet()) {
                Object e = expectedMap.get(key);
                Object a = actualMap.get(key);
                if (volatileKeys.contains(key)) {
                    if (!kind(e).equals(kind(a))) {
                        diffs.add(path + "." + key + ": expected a " + kind(e) + " but was " + a);
                    }
                } else {
                    compare(path + "." + key, e, a, volatileKeys, diffs);
                }
            }
        } else if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            if (expectedList.size() != actualList.size()) {
                diffs.add(path + ": expected " + expectedList.size() + " items but were " + actualList.size());
                return;
            }
            for (int i = 0; i < expectedList.size(); i++) {
                compare(path + "[" + i + "]", expectedList.get(i), actualList.get(i), volatileKeys, diffs);
            }
        } else if (expected instanceof Number e && actual instanceof Number a) {
            if (Math.abs(e.doubleValue() - a.doubleValue()) > NUMBER_TOLERANCE) {
                diffs.add(path + ": expected " + e + " but was " + a);
            }
        } else if (!Objects.equals(expected, actual)) {
            diffs.add(path + ": expected " + expected + " but was " + actual);
        }
    }

    private static String kind(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Map) {
            return "object";
        }
        if (value instanceof List) {
            return "array";
        }
        return value.getClass().getSimpleName().toLowerCase();
    }
}
