package com.sswingg.api.session;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sswingg.api.auth.AuthInterceptor;
import com.sswingg.api.auth.RequireAuth;
import com.sswingg.api.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Body is optional: {"device_id": "..."}. */
    @RequireAuth
    @PostMapping("/session/start")
    public Map<String, Object> start(
            @RequestAttribute(AuthInterceptor.UID) String uid,
            @RequestBody(required = false) Map<String, Object> body) {
        Object requested = body == null ? null : body.get("device_id");
        String deviceId = requested == null ? SessionService.DEFAULT_DEVICE_ID : String.valueOf(requested);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "started");
        response.put("session_id", sessionService.start(uid, deviceId));
        return response;
    }

    @RequireAuth
    @PostMapping("/session/stop")
    public Map<String, Object> stop(@RequestAttribute(AuthInterceptor.UID) String uid) {
        StoppedSession stopped = sessionService.stop(uid)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No active session"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "stopped");
        if (stopped.analytics() == null) {
            response.put("session_id", stopped.sessionId());
            response.put("analytics", null);
            response.put("message", "No data recorded");
        } else {
            response.put("analytics", stopped.analytics().toDocument());
        }
        return response;
    }

    @RequireAuth
    @GetMapping("/sessions")
    public Map<String, Object> history(@RequestAttribute(AuthInterceptor.UID) String uid) {
        return sessionService.history(uid);
    }

    @RequireAuth
    @GetMapping("/session/{sessionId}")
    public Map<String, Object> get(
            @RequestAttribute(AuthInterceptor.UID) String uid, @PathVariable String sessionId) {
        return sessionService.get(uid, sessionId);
    }

    @RequireAuth
    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> delete(
            @RequestAttribute(AuthInterceptor.UID) String uid, @PathVariable String sessionId) {
        sessionService.delete(uid, sessionId);
        return Map.of("status", "deleted");
    }
}
