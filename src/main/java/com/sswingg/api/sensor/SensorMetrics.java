package com.sswingg.api.sensor;

/** Values derived from one reading, rounded the way the Flask backend rounded them. */
public record SensorMetrics(
        double speedKmh,
        double powerWatts,
        double accelG,
        double backliftDeg,
        String shotDirection,
        boolean impactDetected,
        double angularVelocity) {
}
