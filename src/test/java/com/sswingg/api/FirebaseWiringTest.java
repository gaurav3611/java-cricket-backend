package com.sswingg.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Starts the application with the real Firebase Admin SDK and a fake key, without mocks.
 * It proves that a configured key switches demo mode off and that the real token verifier
 * rejects bad tokens. It cannot prove that a genuine token is accepted or that Firestore
 * calls succeed, because that needs a real Firebase project.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FirebaseWiringTest {

    @DynamicPropertySource
    static void firebaseProperties(DynamicPropertyRegistry registry) {
        Path keyFile = FakeServiceAccount.writeKeyFile();
        registry.add("sswingg.firebase.service-account-json", () -> "");
        registry.add("sswingg.firebase.service-account-path", keyFile::toString);
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void aConfiguredKeySwitchesDemoModeOff() {
        Map<String, Object> health = TestHttp.parseObject(new TestHttp(port).get("/api/health", Map.of()));
        assertEquals(false, health.get("demo_mode"));
    }

    @Test
    void protectedRoutesNeedAToken() {
        HttpResponse<String> response = new TestHttp(port).get("/api/profile", Map.of());
        assertEquals(401, response.statusCode());
        assertEquals(Map.of("error", "Missing authorization token"), TestHttp.parse(response.body()));
    }

    @Test
    void theRealVerifierRejectsBadTokens() {
        TestHttp http = new TestHttp(port);
        for (String token : new String[] {"not-a-real-token", "demo-token", "aaa.bbb.ccc"}) {
            HttpResponse<String> response = http.get("/api/sessions", Map.of("Authorization", "Bearer " + token));
            assertEquals(401, response.statusCode(), token);
            assertEquals(Map.of("error", "Invalid or expired token"), TestHttp.parse(response.body()), token);
        }
    }

    @Test
    void openRoutesStillWork() {
        TestHttp http = new TestHttp(port);
        assertEquals(200, http.get("/api/esp32/status", Map.of()).statusCode());
        assertEquals(200, http.postJson("/api/sensor-data", Map.of(), "{\"device_id\":\"BAT_W\",\"gy\":900}").statusCode());
    }
}
