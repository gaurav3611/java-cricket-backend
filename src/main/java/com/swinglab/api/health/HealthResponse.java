package com.swinglab.api.health;

public record HealthResponse(
        String status, boolean demoMode, int connectedClients, int activeSessions, int liveDevices) {
}
