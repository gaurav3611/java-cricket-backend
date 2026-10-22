package com.sswingg.api.profile;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sswingg.api.auth.AuthInterceptor;
import com.sswingg.api.error.ApiException;
import com.sswingg.api.error.GlobalExceptionHandler;
import com.sswingg.api.firebase.FirebaseContext;
import com.sswingg.api.session.SessionRepository;
import com.sswingg.api.util.PyMath;
import com.sswingg.api.util.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final FirebaseContext firebase;
    private final UserDirectory userDirectory;
    private final UserProfileRepository profiles;
    private final SessionRepository sessions;

    public ProfileService(FirebaseContext firebase, UserDirectory userDirectory,
            UserProfileRepository profiles, SessionRepository sessions) {
        this.firebase = firebase;
        this.userDirectory = userDirectory;
        this.profiles = profiles;
        this.sessions = sessions;
    }

    /** The user's account details plus stats added up from their stored sessions. */
    public Map<String, Object> getProfile(String uid) {
        Map<String, Object> profile = new LinkedHashMap<>();
        if (firebase.isDemoMode()) {
            profile.put("uid", AuthInterceptor.DEMO_UID);
            profile.put("email", "demo@sswingg.com");
            profile.put("display_name", "Demo User");
            profile.put("stats", stats(0, 0, 0, 0));
            return profile;
        }

        // When the account lookup fails the response carries the stats only, as in the Flask app.
        userDirectory.find(uid).ifPresent(user -> {
            profile.put("uid", user.uid());
            profile.put("email", user.email());
            profile.put("display_name", user.displayName());
            profile.put("photo_url", user.photoUrl());
            profile.put("created_at", user.createdAt());
        });
        profile.put("stats", aggregateStats(uid));
        return profile;
    }

    private Map<String, Object> aggregateStats(String uid) {
        long totalSessions = 0;
        long totalSwings = 0;
        long bestScore = 0;
        double avgSpeed = 0;
        try {
            double speedSum = 0;
            for (Map<String, Object> session : sessions.findAllByUid(uid)) {
                totalSessions++;
                totalSwings += number(session, "swing_count").longValue();
                bestScore = Math.max(bestScore, number(session, "overall_score").longValue());
                speedSum += number(session, "avg_speed_kmh").doubleValue();
            }
            avgSpeed = PyMath.round(speedSum / Math.max(1, totalSessions), 1);
        } catch (RuntimeException e) {
            log.error("[ERR] Profile stats error: {}", e.getMessage());
        }
        return stats(totalSessions, totalSwings, bestScore, avgSpeed);
    }

    private static Map<String, Object> stats(long totalSessions, long totalSwings, long bestScore, double avgSpeed) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_sessions", totalSessions);
        stats.put("total_swings", totalSwings);
        stats.put("best_score", bestScore);
        stats.put("avg_speed", avgSpeed);
        return stats;
    }

    private static Number number(Map<String, Object> document, String key) {
        return document.get(key) instanceof Number value ? value : 0;
    }

    /** Body fields: display_name, team, batting_style. Missing fields get the Flask defaults. */
    public String updateProfile(String uid, Map<String, Object> body) {
        if (firebase.isDemoMode()) {
            return "ok";
        }
        Map<String, Object> data = body == null ? Map.of() : body;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("uid", uid);
        fields.put("display_name", data.containsKey("display_name") ? data.get("display_name") : "");
        fields.put("team", data.containsKey("team") ? data.get("team") : "");
        fields.put("batting_style", data.containsKey("batting_style") ? data.get("batting_style") : "right");
        fields.put("updated_at", Timestamps.isoUtc(Instant.now()));
        try {
            profiles.merge(uid, fields);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, GlobalExceptionHandler.describe(e));
        }
        return "updated";
    }
}
