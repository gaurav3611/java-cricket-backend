package com.sswingg.api.health;

import com.sswingg.api.firebase.FirebaseContext;
import com.sswingg.api.sensor.SensorService;
import com.sswingg.api.session.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final FirebaseContext firebase;
    private final SessionService sessionService;
    private final SensorService sensorService;

    public HealthController(FirebaseContext firebase, SessionService sessionService, SensorService sensorService) {
        this.firebase = firebase;
        this.sessionService = sessionService;
        this.sensorService = sensorService;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        // connected_clients counted Socket.IO connections in the Flask app. This API has no
        // socket layer, so the value is always 0. The field stays so the response shape is unchanged.
        return new HealthResponse("healthy", firebase.isDemoMode(), 0,
                sessionService.activeSessionCount(), sensorService.liveDeviceCount());
    }
}
