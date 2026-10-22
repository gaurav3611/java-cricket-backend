package com.sswingg.api.firebase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Owns the Firebase Admin SDK and decides whether the API runs in demo mode.
 *
 * Credentials come from FIREBASE_SERVICE_ACCOUNT_JSON (the whole key as text, as api/index.py
 * read it) or from the file at FIREBASE_SERVICE_ACCOUNT_PATH (as backend/app.py read it).
 * With neither present the API runs in demo mode: no token checks and no Firestore.
 * Credentials that are present but unreadable stop the application from starting, so a
 * broken key can never silently turn authentication off.
 */
@Component
public class FirebaseContext {

    private static final Logger log = LoggerFactory.getLogger(FirebaseContext.class);

    private final FirebaseApp app;

    public FirebaseContext(
            @Value("${sswingg.firebase.service-account-json:}") String serviceAccountJson,
            @Value("${sswingg.firebase.service-account-path:serviceAccountKey.json}") String serviceAccountPath) {
        this.app = initialize(serviceAccountJson, serviceAccountPath);
    }

    private static FirebaseApp initialize(String json, String path) {
        String source;
        InputStream stream;
        try {
            if (json != null && !json.isBlank()) {
                source = "FIREBASE_SERVICE_ACCOUNT_JSON";
                stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            } else if (path != null && !path.isBlank() && Files.isRegularFile(Path.of(path))) {
                source = Path.of(path).toAbsolutePath().toString();
                stream = Files.newInputStream(Path.of(path));
            } else {
                log.warn("[WARNING] No Firebase service account found (looked for FIREBASE_SERVICE_ACCOUNT_JSON"
                        + " and the file '{}'). Running in DEMO MODE: no authentication, nothing is stored.", path);
                return null;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not open the Firebase service account file " + path, e);
        }

        try (stream) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(ServiceAccountCredentials.fromStream(stream))
                    .build();
            // A named app, so several application contexts in one JVM (tests) never share state.
            FirebaseApp initialized = FirebaseApp.initializeApp(options, "sswingg-" + UUID.randomUUID());
            log.info("[OK] Firebase Admin SDK initialized from {}", source);
            return initialized;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Firebase credentials from " + source + " are not a valid service account key", e);
        }
    }

    public boolean isDemoMode() {
        return app == null;
    }

    public FirebaseAuth auth() {
        return FirebaseAuth.getInstance(requireApp());
    }

    public Firestore firestore() {
        return FirestoreClient.getFirestore(requireApp());
    }

    private FirebaseApp requireApp() {
        if (app == null) {
            throw new IllegalStateException("Firebase is not configured (demo mode)");
        }
        return app;
    }

    @PreDestroy
    void shutdown() {
        if (app != null) {
            app.delete();
        }
    }
}
