package com.sswingg.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sswingg.api.auth.TokenVerifier;
import com.sswingg.api.firebase.FirebaseContext;
import com.sswingg.api.profile.UserDirectory;
import com.sswingg.api.profile.UserInfo;
import com.sswingg.api.profile.UserProfileRepository;
import com.sswingg.api.session.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Checks the examples on the API reference page that need a signed-in user: the ones marked
 * "in": "secured" in static/docs/endpoints.json. Firebase is replaced by stand-ins, set up
 * for each example by {@link #arrange}. An example with a case this test does not know fails,
 * so a new example cannot be added without a check.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiDocsSecuredTest {

    private static final String DEFAULT_UID = "docs-user";

    @MockitoBean
    FirebaseContext firebase;
    @MockitoBean
    TokenVerifier tokenVerifier;
    @MockitoBean
    SessionRepository sessions;
    @MockitoBean
    UserDirectory users;
    @MockitoBean
    UserProfileRepository profiles;

    @Value("${local.server.port}")
    int port;

    private final DocsSpec spec = DocsSpec.load();
    private TestHttp http;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
        when(firebase.isDemoMode()).thenReturn(false);
        when(tokenVerifier.verify(anyString())).thenAnswer(call -> {
            String token = call.getArgument(0);
            return token.startsWith("token-") ? Optional.of(token.substring(6)) : Optional.empty();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void everySignedInExampleIsWhatTheApiReallyAnswers() {
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (Map<String, Object> endpoint : spec.endpoints()) {
            for (Map<String, Object> response : DocsSpec.responses(endpoint)) {
                Map<String, Object> check = DocsSpec.verify(response);
                if (!"secured".equals(check.get("in"))) {
                    continue;
                }
                List<String> cases = check.get("cases") instanceof List<?> many
                        ? (List<String>) many
                        : List.of((String) check.get("case"));
                for (String caseName : cases) {
                    reset(sessions, users, profiles);
                    String uid = (String) check.getOrDefault("uid", DEFAULT_UID);
                    String path = (String) DocsSpec.tryRequest(endpoint).get("path");
                    arrange((String) endpoint.get("id"), caseName, uid, path, response, check);

                    HttpResponse<String> actual = send(endpoint, Map.of("Authorization", "Bearer token-" + uid));
                    compare(DocsSpec.label(endpoint, response) + " [" + caseName + "]", response, actual, check, failures);
                    confirmSideEffect((String) endpoint.get("id"), caseName, uid, path);
                    checked++;
                }
            }
        }

        // The two 401 answers apply to every endpoint that needs sign-in.
        for (Map<String, Object> response : spec.commonResponses()) {
            Map<String, Object> check = DocsSpec.verify(response);
            if (!"secured".equals(check.get("in"))) {
                continue;
            }
            Map<String, String> headers = switch ((String) check.get("case")) {
                case "missing_token" -> Map.of();
                case "invalid_token" -> Map.of("Authorization", "Bearer forged");
                default -> fail("No check for the common case " + check.get("case"));
            };
            for (Map<String, Object> endpoint : spec.endpoints()) {
                if (Boolean.TRUE.equals(endpoint.get("auth"))) {
                    compare(endpoint.get("method") + " " + endpoint.get("path") + " [" + check.get("case") + "]",
                            response, send(endpoint, headers), check, failures);
                    checked++;
                }
            }
        }

        assertTrue(checked >= 25, "expected at least 25 signed-in examples, checked " + checked);
        assertTrue(failures.isEmpty(), () -> failures.size() + " documented examples differ from the API:\n"
                + String.join("\n", failures));
    }

    /** Puts the stand-ins in the state that produces the documented response. */
    @SuppressWarnings("unchecked")
    private void arrange(String endpointId, String caseName, String uid, String path,
            Map<String, Object> response, Map<String, Object> check) {
        Object body = response.get("body");
        String sessionId = path.substring(path.lastIndexOf('/') + 1);
        Map<String, Object> fixtures = check.get("fixtures") instanceof Map<?, ?> f ? (Map<String, Object>) f : Map.of();
        List<Map<String, Object>> storedSessions =
                (List<Map<String, Object>>) fixtures.getOrDefault("sessions", List.of());

        switch (endpointId + ":" + caseName) {
            case "sessions-list:owner" ->
                    when(sessions.findRecentByUid(uid, 50))
                            .thenReturn((List<Map<String, Object>>) ((Map<String, Object>) body).get("sessions"));
            case "sessions-list:storage_failure" ->
                    when(sessions.findRecentByUid(uid, 50)).thenThrow(failure(body));
            case "session-get:owner" ->
                    when(sessions.findById(sessionId)).thenReturn(Optional.of((Map<String, Object>) body));
            case "session-delete:owner" ->
                    when(sessions.findById(sessionId)).thenReturn(Optional.of(Map.of("uid", uid)));
            case "session-get:not_owner", "session-delete:not_owner" ->
                    when(sessions.findById(sessionId)).thenReturn(Optional.of(Map.of("uid", "someone-else")));
            case "session-get:not_found", "session-delete:not_found" ->
                    when(sessions.findById(sessionId)).thenReturn(Optional.empty());
            case "session-get:storage_failure", "session-delete:storage_failure" ->
                    when(sessions.findById(sessionId)).thenThrow(failure(body));
            case "profile-get:owner" -> {
                Map<String, Object> profile = (Map<String, Object>) body;
                when(users.find(uid)).thenReturn(Optional.of(new UserInfo(
                        (String) profile.get("uid"), (String) profile.get("email"),
                        (String) profile.get("display_name"), (String) profile.get("photo_url"),
                        ((Number) profile.get("created_at")).longValue())));
                when(sessions.findAllByUid(uid)).thenReturn(storedSessions);
            }
            case "profile-get:stats_only" -> {
                when(users.find(uid)).thenReturn(Optional.empty());
                when(sessions.findAllByUid(uid)).thenReturn(storedSessions);
            }
            case "profile-update:owner" -> {
                // Nothing to prepare. The write is confirmed afterwards.
            }
            case "profile-update:storage_failure" ->
                    doThrow(failure(body)).when(profiles).merge(eq(uid), any());
            default -> fail("ApiDocsSecuredTest has no arrangement for " + endpointId + ":" + caseName
                    + ". Add one for the new example in endpoints.json.");
        }
    }

    private void confirmSideEffect(String endpointId, String caseName, String uid, String path) {
        String sessionId = path.substring(path.lastIndexOf('/') + 1);
        switch (endpointId + ":" + caseName) {
            case "session-delete:owner" -> verify(sessions).delete(sessionId);
            case "profile-update:owner" -> verify(profiles).merge(eq(uid), any());
            default -> {
                // Reads have no side effect to confirm.
            }
        }
    }

    /** A storage failure that carries the documented error text. */
    private static IllegalStateException failure(Object body) {
        return new IllegalStateException((String) ((Map<?, ?>) body).get("error"));
    }

    private HttpResponse<String> send(Map<String, Object> endpoint, Map<String, String> authHeaders) {
        Map<String, Object> defaults = DocsSpec.tryRequest(endpoint);
        String method = (String) endpoint.get("method");
        boolean hasBody = defaults.containsKey("body") && (method.equals("POST") || method.equals("PUT"));
        Map<String, String> headers = new HashMap<>(authHeaders);
        if (hasBody) {
            headers.putAll(TestHttp.JSON_HEADERS);
        }
        return http.send(method, (String) defaults.get("path"), headers,
                hasBody ? TestHttp.JSON.writeValueAsString(defaults.get("body")) : null);
    }

    private void compare(String label, Map<String, Object> response, HttpResponse<String> actual,
            Map<String, Object> check, List<String> failures) {
        List<String> diffs = new ArrayList<>();
        if (actual.statusCode() != (Integer) response.get("status")) {
            diffs.add("status: documented " + response.get("status") + " but was " + actual.statusCode()
                    + " with body " + actual.body());
        } else {
            diffs.addAll(JsonCompare.diff(response.get("body"), TestHttp.parse(actual.body()), spec.volatileKeys(check)));
        }
        if (!diffs.isEmpty()) {
            failures.add(label + "\n    " + String.join("\n    ", diffs));
        }
    }
}
