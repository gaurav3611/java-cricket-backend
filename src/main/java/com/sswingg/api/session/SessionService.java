package com.sswingg.api.session;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sswingg.api.error.ApiException;
import com.sswingg.api.error.GlobalExceptionHandler;
import com.sswingg.api.firebase.FirebaseContext;
import com.sswingg.api.sensor.ProcessedReading;
import com.sswingg.api.sensor.SensorMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Recording sessions and their stored history.
 *
 * Sessions in progress live in memory, one per user. They are lost on restart and are not
 * shared between instances, so the API must run as a single instance, like the Flask app.
 */
@Service
public class SessionService {

    public static final String DEFAULT_DEVICE_ID = "CRICPRO_BAT_001";

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final int HISTORY_LIMIT = 50;
    private static final String DEMO_UNAVAILABLE = "Not available in demo mode";

    private final Map<String, ActiveSession> activeByUid = new ConcurrentHashMap<>();
    private final FirebaseContext firebase;
    private final SessionRepository repository;

    public SessionService(FirebaseContext firebase, SessionRepository repository) {
        this.firebase = firebase;
        this.repository = repository;
    }

    /** Starts a session for the user. A session the user already had running is discarded. */
    public String start(String uid, String deviceId) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        activeByUid.put(uid, new ActiveSession(sessionId, uid, deviceId, Instant.now()));
        return sessionId;
    }

    /** Adds a reading to every running session that listens to the reading's device. */
    public void accumulate(ProcessedReading reading, SensorMath.Result result) {
        for (ActiveSession session : activeByUid.values()) {
            if (session.deviceId().equals(reading.deviceId())) {
                session.add(reading, result);
            }
        }
    }

    /** Stops the user's session. Empty when the user has no session running. */
    public Optional<StoppedSession> stop(String uid) {
        ActiveSession session = activeByUid.remove(uid);
        if (session == null) {
            return Optional.empty();
        }
        ActiveSession.Snapshot snapshot = session.snapshot();
        if (snapshot.dataPoints().isEmpty()) {
            return Optional.of(new StoppedSession(snapshot.sessionId(), null));
        }

        SessionAnalytics analytics = SessionAnalytics.compute(snapshot, Instant.now());
        if (!firebase.isDemoMode()) {
            // A failed save is logged and the analytics are still returned, as in the Flask app.
            try {
                repository.save(analytics.sessionId(), analytics.toDocument());
                log.info("[DB] Session {} saved to Firestore", analytics.sessionId());
            } catch (RuntimeException e) {
                log.error("[ERR] Firestore save failed: {}", e.getMessage());
            }
        }
        return Optional.of(new StoppedSession(snapshot.sessionId(), analytics));
    }

    public int activeSessionCount() {
        return activeByUid.size();
    }

    /** The user's stored sessions. A Firestore failure is reported inside a 200 response, as before. */
    public Map<String, Object> history(String uid) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (firebase.isDemoMode()) {
            response.put("sessions", List.of());
            return response;
        }
        try {
            response.put("sessions", repository.findRecentByUid(uid, HISTORY_LIMIT));
        } catch (RuntimeException e) {
            log.error("[ERR] Get sessions failed: {}", e.getMessage());
            response.put("sessions", List.of());
            response.put("error", GlobalExceptionHandler.describe(e));
        }
        return response;
    }

    public Map<String, Object> get(String uid, String sessionId) {
        Map<String, Object> document = find(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!uid.equals(document.get("uid"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Unauthorized");
        }
        return document;
    }

    public void delete(String uid, String sessionId) {
        Optional<Map<String, Object>> document = find(sessionId);
        if (document.isEmpty() || !uid.equals(document.get().get("uid"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found or unauthorized");
        }
        try {
            repository.delete(sessionId);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, GlobalExceptionHandler.describe(e));
        }
    }

    private Optional<Map<String, Object>> find(String sessionId) {
        if (firebase.isDemoMode()) {
            throw new ApiException(HttpStatus.NOT_FOUND, DEMO_UNAVAILABLE);
        }
        try {
            return repository.findById(sessionId);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, GlobalExceptionHandler.describe(e));
        }
    }
}
