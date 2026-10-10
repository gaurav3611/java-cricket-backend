package com.swinglab.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Keeps the API reference page at /docs truthful. The page shows what is in
 * static/docs/endpoints.json, so these tests compare that file with the running API:
 * the list of endpoints, and every example that can be produced in demo mode.
 * ApiDocsSecuredTest covers the examples that need a signed-in user.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "swinglab.firebase.service-account-json=",
            "swinglab.firebase.service-account-path=target/no-such-service-account.json",
            // No frontend, as in a deployment of this project on its own.
            "swinglab.frontend-dir=target/no-such-frontend"
        })
@DirtiesContext // the demo simulation is started by one of the examples
class ApiDocsTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private final DocsSpec spec = DocsSpec.load();

    @Test
    void everyEndpointIsDocumentedAndEveryDocumentedEndpointExists() {
        Set<String> inCode = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    inCode.add(method + " " + withoutParameterNames(pattern));
                }
            }
        }
        Set<String> documented = new TreeSet<>();
        for (Map<String, Object> endpoint : spec.endpoints()) {
            documented.add(endpoint.get("method") + " " + withoutParameterNames((String) endpoint.get("path")));
        }
        assertEquals(inCode, documented,
                "static/docs/endpoints.json must list exactly the /api endpoints that the controllers define");
    }

    @Test
    void everyResponseHasAnExampleAndAWayToCheckIt() {
        List<Map<String, Object>> all = new ArrayList<>(spec.commonResponses());
        spec.endpoints().forEach(endpoint -> all.addAll(DocsSpec.responses(endpoint)));
        assertTrue(all.size() >= 30, "expected the full list of responses, found " + all.size());
        for (Map<String, Object> response : all) {
            assertTrue(response.get("status") instanceof Integer, "status missing in " + response);
            assertTrue(response.get("when") instanceof String, "when missing in " + response);
            assertTrue(response.containsKey("body"), "body missing in " + response);
            assertNotNull(DocsSpec.verify(response), "verify missing in " + response);
            assertTrue(Set.of("demo", "secured").contains(DocsSpec.verify(response).get("in")),
                    "verify.in must be demo or secured in " + response);
        }
    }

    @Test
    void everyDemoModeExampleIsWhatTheApiReallyAnswers() {
        TestHttp http = new TestHttp(port);
        List<String> failures = new ArrayList<>();
        int checked = 0;

        // Endpoint examples run in the order of the file. Each one sets up what it needs.
        for (Map<String, Object> endpoint : spec.endpoints()) {
            for (Map<String, Object> response : DocsSpec.responses(endpoint)) {
                if ("demo".equals(DocsSpec.verify(response).get("in"))) {
                    check(http, endpoint, response, DocsSpec.label(endpoint, response), failures);
                    checked++;
                }
            }
        }
        for (Map<String, Object> response : spec.commonResponses()) {
            if ("demo".equals(DocsSpec.verify(response).get("in"))) {
                check(http, null, response, "common -> " + response.get("status") + " (" + response.get("when") + ")", failures);
                checked++;
            }
        }

        assertTrue(checked >= 15, "expected at least 15 demo mode examples, checked " + checked);
        assertTrue(failures.isEmpty(), () -> failures.size() + " documented examples differ from the API:\n"
                + String.join("\n", failures));
    }

    @SuppressWarnings("unchecked")
    private void check(TestHttp http, Map<String, Object> endpoint, Map<String, Object> response,
            String label, List<String> failures) {
        Map<String, Object> verify = DocsSpec.verify(response);

        if (verify.get("setup") instanceof List<?> setup) {
            for (Map<String, Object> step : (List<Map<String, Object>>) setup) {
                String body = step.containsKey("body") ? TestHttp.JSON.writeValueAsString(step.get("body")) : null;
                http.send((String) step.get("method"), (String) step.get("path"),
                        body == null ? Map.of() : TestHttp.JSON_HEADERS, body);
            }
        }

        Map<String, Object> request = verify.get("request") instanceof Map<?, ?> r ? (Map<String, Object>) r : Map.of();
        Map<String, Object> defaults = endpoint == null ? Map.of() : DocsSpec.tryRequest(endpoint);
        String method = (String) request.getOrDefault("method", endpoint == null ? null : endpoint.get("method"));
        String path = (String) request.getOrDefault("path", defaults.get("path"));
        String body;
        if (request.containsKey("raw")) {
            body = (String) request.get("raw");
        } else if (request.containsKey("body")) {
            body = TestHttp.JSON.writeValueAsString(request.get("body"));
        } else if (defaults.containsKey("body") && (method.equals("POST") || method.equals("PUT"))) {
            body = TestHttp.JSON.writeValueAsString(defaults.get("body"));
        } else {
            body = null;
        }
        Map<String, String> headers = request.get("headers") instanceof Map<?, ?> h
                ? (Map<String, String>) h
                : (body == null ? Map.of() : TestHttp.JSON_HEADERS);

        HttpResponse<String> actual = http.send(method, path, headers, body);

        List<String> diffs = new ArrayList<>();
        if (actual.statusCode() != (Integer) response.get("status")) {
            diffs.add("status: documented " + response.get("status") + " but was " + actual.statusCode()
                    + " with body " + actual.body());
        } else {
            diffs.addAll(JsonCompare.diff(response.get("body"), TestHttp.parse(actual.body()), spec.volatileKeys(verify)));
        }
        if (!diffs.isEmpty()) {
            failures.add(label + "\n    " + String.join("\n    ", diffs));
        }
    }

    @Test
    void theReferencePageIsServed() {
        TestHttp http = new TestHttp(port);

        for (String address : List.of("/docs", "/docs/")) {
            HttpResponse<String> redirect = http.get(address, Map.of());
            assertEquals(302, redirect.statusCode(), address);
            assertTrue(redirect.headers().firstValue("Location").orElse("").endsWith("/docs/index.html"), address);
        }

        HttpResponse<String> page = http.get("/docs/index.html", Map.of());
        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(page.body().contains("endpoints.json"), "the page should load its endpoint list");

        HttpResponse<String> list = http.get("/docs/endpoints.json", Map.of());
        assertEquals(200, list.statusCode());
        assertTrue(list.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertEquals(spec.root(), TestHttp.parse(list.body()));
    }

    @Test
    void withNoFrontendTheStartPageLeadsToTheReference() {
        HttpResponse<String> start = new TestHttp(port).get("/", Map.of());
        assertEquals(200, start.statusCode());
        assertTrue(start.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(start.body().contains("url=/docs"), "the fallback start page should send visitors to /docs");
    }

    /** /api/session/{sessionId} and /api/session/{id} are the same endpoint. */
    private static String withoutParameterNames(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", "{}");
    }
}
