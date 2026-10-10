package com.swinglab.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.swinglab.api.auth.TokenVerifier;
import com.swinglab.api.firebase.FirebaseContext;
import com.swinglab.api.profile.UserDirectory;
import com.swinglab.api.profile.UserInfo;
import com.swinglab.api.profile.UserProfileRepository;
import com.swinglab.api.session.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The behaviour with Firebase configured: token checks, ownership rules and what is read
 * from and written to storage. Firebase itself is replaced by stand-ins, so these tests
 * check this API's logic and not Firebase.
 *
 * Every test signs in as its own user so that sessions held in memory do not interfere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecuredModeTest {

    private static final String[][] PROTECTED_ROUTES = {
        {"GET", "/api/profile"}, {"PUT", "/api/profile"}, {"GET", "/api/sessions"},
        {"GET", "/api/session/abc"}, {"DELETE", "/api/session/abc"},
        {"POST", "/api/session/start"}, {"POST", "/api/session/stop"},
    };

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

    TestHttp http;

    @BeforeEach
    void setUp() {
        http = new TestHttp(port);
        when(firebase.isDemoMode()).thenReturn(false);
        // The token "token-<uid>" belongs to <uid>. Every other token is invalid.
        when(tokenVerifier.verify(anyString())).thenAnswer(call -> {
            String token = call.getArgument(0);
            return token.startsWith("token-") ? Optional.of(token.substring(6)) : Optional.empty();
        });
    }

    private static Map<String, String> as(String uid) {
        return Map.of("Authorization", "Bearer token-" + uid);
    }

    private static Map<String, Object> error(String message) {
        return Map.of("error", message);
    }

    // ---------------------------------------------------------------- authentication

    @Test
    void healthReportsThatDemoModeIsOff() {
        assertEquals(false, TestHttp.parseObject(http.get("/api/health", Map.of())).get("demo_mode"));
    }

    @Test
    void protectedRoutesRejectAMissingToken() {
        for (String[] route : PROTECTED_ROUTES) {
            for (Map<String, String> headers : List.of(Map.<String, String>of(), Map.of("Authorization", "Basic abc"))) {
                HttpResponse<String> response = http.send(route[0], route[1], headers, null);
                assertEquals(401, response.statusCode(), route[0] + " " + route[1]);
                assertEquals(error("Missing authorization token"), TestHttp.parse(response.body()));
            }
        }
        verifyNoInteractions(sessions, profiles, users);
    }

    @Test
    void protectedRoutesRejectAnInvalidToken() {
        for (String[] route : PROTECTED_ROUTES) {
            HttpResponse<String> response =
                    http.send(route[0], route[1], Map.of("Authorization", "Bearer forged"), null);
            assertEquals(401, response.statusCode(), route[0] + " " + route[1]);
            assertEquals(error("Invalid or expired token"), TestHttp.parse(response.body()));
        }
        verifyNoInteractions(sessions, profiles, users);
    }

    @Test
    void aRejectedRequestStillCarriesCorsHeadersSoBrowsersCanReadIt() {
        HttpResponse<String> response = http.get("/api/profile", Map.of("Origin", "https://app.example"));
        assertEquals(401, response.statusCode());
        assertEquals("https://app.example", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    void openRoutesNeedNoToken() {
        assertEquals(200, http.get("/api/health", Map.of()).statusCode());
        assertEquals(200, http.get("/api/esp32/status", Map.of()).statusCode());
        assertEquals(200, http.postJson("/api/sensor-data", Map.of(), "{\"device_id\":\"BAT_OPEN\",\"gy\":500}").statusCode());
    }

    // ---------------------------------------------------------------- session history

    @Test
    void historyReturnsTheUsersFiftyNewestSessions() {
        List<Map<String, Object>> stored = List.of(
                Map.of("session_id", "aaaa1111", "uid", "u-history", "overall_score", 80L),
                Map.of("session_id", "bbbb2222", "uid", "u-history", "overall_score", 61L));
        when(sessions.findRecentByUid("u-history", 50)).thenReturn(stored);

        HttpResponse<String> response = http.get("/api/sessions", as("u-history"));

        assertEquals(200, response.statusCode());
        assertEquals(TestHttp.parse(TestHttp.JSON.writeValueAsString(Map.of("sessions", stored))),
                TestHttp.parse(response.body()));
        verify(sessions).findRecentByUid("u-history", 50);
    }

    @Test
    void historyReportsAStorageFailureInsideA200Answer() {
        when(sessions.findRecentByUid("u-broken", 50)).thenThrow(new IllegalStateException("The query requires an index"));

        HttpResponse<String> response = http.get("/api/sessions", as("u-broken"));

        assertEquals(200, response.statusCode());
        assertEquals(Map.of("sessions", List.of(), "error", "The query requires an index"), TestHttp.parse(response.body()));
    }

    @Test
    void aSessionCanOnlyBeReadByItsOwner() {
        when(sessions.findById("s-owned")).thenReturn(Optional.of(Map.of("session_id", "s-owned", "uid", "u-owner", "swing_count", 7L)));
        when(sessions.findById("s-missing")).thenReturn(Optional.empty());
        when(sessions.findById("s-boom")).thenThrow(new IllegalStateException("firestore unavailable"));

        HttpResponse<String> owner = http.get("/api/session/s-owned", as("u-owner"));
        assertEquals(200, owner.statusCode());
        assertEquals(Map.of("session_id", "s-owned", "uid", "u-owner", "swing_count", 7), TestHttp.parse(owner.body()));

        HttpResponse<String> stranger = http.get("/api/session/s-owned", as("u-stranger"));
        assertEquals(403, stranger.statusCode());
        assertEquals(error("Unauthorized"), TestHttp.parse(stranger.body()));

        HttpResponse<String> missing = http.get("/api/session/s-missing", as("u-owner"));
        assertEquals(404, missing.statusCode());
        assertEquals(error("Session not found"), TestHttp.parse(missing.body()));

        HttpResponse<String> failure = http.get("/api/session/s-boom", as("u-owner"));
        assertEquals(500, failure.statusCode());
        assertEquals(error("firestore unavailable"), TestHttp.parse(failure.body()));
    }

    @Test
    void aSessionCanOnlyBeDeletedByItsOwner() {
        when(sessions.findById("d-owned")).thenReturn(Optional.of(Map.of("uid", "u-deleter")));
        when(sessions.findById("d-missing")).thenReturn(Optional.empty());

        HttpResponse<String> stranger = http.send("DELETE", "/api/session/d-owned", as("u-stranger"), null);
        assertEquals(404, stranger.statusCode());
        assertEquals(error("Not found or unauthorized"), TestHttp.parse(stranger.body()));

        HttpResponse<String> missing = http.send("DELETE", "/api/session/d-missing", as("u-deleter"), null);
        assertEquals(404, missing.statusCode());
        assertEquals(error("Not found or unauthorized"), TestHttp.parse(missing.body()));
        verify(sessions, never()).delete(anyString());

        HttpResponse<String> owner = http.send("DELETE", "/api/session/d-owned", as("u-deleter"), null);
        assertEquals(200, owner.statusCode());
        assertEquals(Map.of("status", "deleted"), TestHttp.parse(owner.body()));
        verify(sessions).delete("d-owned");
    }

    @Test
    void aFailedDeleteIsReportedAs500() {
        when(sessions.findById("d-boom")).thenReturn(Optional.of(Map.of("uid", "u-deleter2")));
        doThrow(new IllegalStateException("firestore unavailable")).when(sessions).delete("d-boom");

        HttpResponse<String> response = http.send("DELETE", "/api/session/d-boom", as("u-deleter2"), null);
        assertEquals(500, response.statusCode());
        assertEquals(error("firestore unavailable"), TestHttp.parse(response.body()));
    }

    // ---------------------------------------------------------------- profile

    @Test
    void profileCombinesTheAccountWithStatsFromStoredSessions() {
        when(users.find("u-profile")).thenReturn(Optional.of(
                new UserInfo("u-profile", "asha@example.org", "Asha", "https://example.org/a.png", 1_700_000_000_000L)));
        when(sessions.findAllByUid("u-profile")).thenReturn(List.of(
                Map.of("swing_count", 10L, "overall_score", 80L, "avg_speed_kmh", 50.0),
                Map.of("swing_count", 5L, "overall_score", 60L, "avg_speed_kmh", 40.5)));

        HttpResponse<String> response = http.get("/api/profile", as("u-profile"));

        assertEquals(200, response.statusCode());
        // The average is exactly 45.25, which Python rounded to the even digit: 45.2.
        assertEquals(TestHttp.parse("""
                {"uid": "u-profile", "email": "asha@example.org", "display_name": "Asha",
                 "photo_url": "https://example.org/a.png", "created_at": 1700000000000,
                 "stats": {"total_sessions": 2, "total_swings": 15, "best_score": 80, "avg_speed": 45.2}}
                """), TestHttp.parse(response.body()));
    }

    @Test
    void profileTreatsMissingSessionFieldsAsZero() {
        when(users.find("u-sparse")).thenReturn(Optional.empty());
        when(sessions.findAllByUid("u-sparse")).thenReturn(List.of(
                Map.of("swing_count", 4L, "overall_score", 70L, "avg_speed_kmh", 30.0), Map.of()));

        HttpResponse<String> response = http.get("/api/profile", as("u-sparse"));

        // With no account record the answer holds the stats only, as in the Flask app.
        assertEquals(TestHttp.parse("""
                {"stats": {"total_sessions": 2, "total_swings": 4, "best_score": 70, "avg_speed": 15.0}}
                """), TestHttp.parse(response.body()));
    }

    @Test
    void profileStatsFallBackToZeroWhenStorageFails() {
        when(users.find("u-nostats")).thenReturn(Optional.empty());
        when(sessions.findAllByUid("u-nostats")).thenThrow(new IllegalStateException("firestore unavailable"));

        HttpResponse<String> response = http.get("/api/profile", as("u-nostats"));

        assertEquals(200, response.statusCode());
        assertEquals(TestHttp.parse("""
                {"stats": {"total_sessions": 0, "total_swings": 0, "best_score": 0, "avg_speed": 0.0}}
                """), TestHttp.parse(response.body()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateProfileMergesTheGivenFieldsAndFillsDefaults() {
        HttpResponse<String> response = http.send("PUT", "/api/profile",
                Map.of("Authorization", "Bearer token-u-edit", "Content-Type", "application/json"),
                "{\"display_name\": \"Asha\", \"ignored\": true}");

        assertEquals(200, response.statusCode());
        assertEquals(Map.of("status", "updated"), TestHttp.parse(response.body()));

        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(profiles).merge(eq("u-edit"), fields.capture());
        assertEquals(Set.of("uid", "display_name", "team", "batting_style", "updated_at"), fields.getValue().keySet());
        assertEquals("u-edit", fields.getValue().get("uid"));
        assertEquals("Asha", fields.getValue().get("display_name"));
        assertEquals("", fields.getValue().get("team"));
        assertEquals("right", fields.getValue().get("batting_style"));
        assertTrue(((String) fields.getValue().get("updated_at"))
                .matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{6}\\+00:00"), "Python isoformat expected");
    }

    @Test
    void aFailedProfileUpdateIsReportedAs500() {
        doThrow(new IllegalStateException("firestore unavailable")).when(profiles).merge(eq("u-edit-fail"), any());

        HttpResponse<String> response = http.send("PUT", "/api/profile",
                Map.of("Authorization", "Bearer token-u-edit-fail", "Content-Type", "application/json"), "{}");

        assertEquals(500, response.statusCode());
        assertEquals(error("firestore unavailable"), TestHttp.parse(response.body()));
    }

    // ---------------------------------------------------------------- recording

    @Test
    @SuppressWarnings("unchecked")
    void stoppingASessionStoresExactlyTheAnalyticsItReturns() {
        http.postJson("/api/session/start", as("u-rec"), "{\"device_id\": \"BAT_REC\"}");
        for (int gy : new int[] {200, 900, 1300}) {
            http.postJson("/api/sensor-data", Map.of(), "{\"device_id\":\"BAT_REC\",\"ax\":1.2,\"gy\":" + gy + "}");
        }

        HttpResponse<String> response = http.send("POST", "/api/session/stop", as("u-rec"), null);

        assertEquals(200, response.statusCode());
        Map<String, Object> analytics = (Map<String, Object>) TestHttp.parseObject(response).get("analytics");
        assertEquals("u-rec", analytics.get("uid"));
        assertEquals("BAT_REC", analytics.get("device_id"));
        assertEquals(3, analytics.get("total_readings"));

        ArgumentCaptor<Map<String, Object>> stored = ArgumentCaptor.forClass(Map.class);
        verify(sessions).save(eq((String) analytics.get("session_id")), stored.capture());
        assertEquals(analytics, TestHttp.parse(TestHttp.JSON.writeValueAsString(stored.getValue())));
    }

    @Test
    void stoppingStillAnswersWhenTheAnalyticsCannotBeStored() {
        doThrow(new IllegalStateException("firestore unavailable")).when(sessions).save(anyString(), any());
        http.postJson("/api/session/start", as("u-rec-fail"), "{\"device_id\": \"BAT_FAIL\"}");
        http.postJson("/api/sensor-data", Map.of(), "{\"device_id\":\"BAT_FAIL\",\"gy\":900}");

        HttpResponse<String> response = http.send("POST", "/api/session/stop", as("u-rec-fail"), null);

        assertEquals(200, response.statusCode());
        assertTrue(TestHttp.parseObject(response).get("analytics") instanceof Map);
    }

    @Test
    void aSessionWithNoReadingsIsNotStored() {
        http.postJson("/api/session/start", as("u-empty"), "{\"device_id\": \"BAT_EMPTY\"}");

        HttpResponse<String> response = http.send("POST", "/api/session/stop", as("u-empty"), null);

        assertEquals(200, response.statusCode());
        assertEquals("No data recorded", TestHttp.parseObject(response).get("message"));
        verify(sessions, never()).save(anyString(), any());
    }

    @Test
    void sessionsBelongToTheUserWhoStartedThem() {
        http.postJson("/api/session/start", as("u-first"), "{\"device_id\": \"BAT_SHARED\"}");

        HttpResponse<String> other = http.send("POST", "/api/session/stop", as("u-second"), null);
        assertEquals(400, other.statusCode());
        assertEquals(error("No active session"), TestHttp.parse(other.body()));

        assertEquals(200, http.send("POST", "/api/session/stop", as("u-first"), null).statusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void startingWithoutABodyUsesTheDefaultDevice() {
        // Flask answered 415 here. This API accepts the request, see README.md.
        assertEquals(200, http.send("POST", "/api/session/start", as("u-default"), null).statusCode());
        http.postJson("/api/sensor-data", Map.of(), "{\"device_id\":\"CRICPRO_BAT_001\",\"gy\":900}");

        Map<String, Object> analytics = (Map<String, Object>) TestHttp.parseObject(
                http.send("POST", "/api/session/stop", as("u-default"), null)).get("analytics");
        assertEquals("CRICPRO_BAT_001", analytics.get("device_id"));
        assertEquals(1, analytics.get("total_readings"));
    }
}
