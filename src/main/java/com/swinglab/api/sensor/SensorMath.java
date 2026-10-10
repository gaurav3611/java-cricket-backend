package com.swinglab.api.sensor;

/**
 * The metric formulas from the /api/sensor-data route of backend/app.py, unchanged.
 * Constants and the order of operations are kept, so results match the Flask backend.
 */
public final class SensorMath {

    /** Gyro noise below this many degrees per second counts as no rotation. */
    static final double GYRO_DEAD_ZONE_DPS = 10.0;
    /** Distance in metres from the handle sensor to the sweet spot. */
    static final double BAT_RADIUS_M = 0.55;
    static final double MAX_SPEED_KMH = 160.0;
    static final double BAT_MASS_KG = 1.2;
    static final double GRAVITY = 9.81;
    static final double IMPACT_THRESHOLD_G = 3.0;
    static final double BACKLIFT_FACTOR = 15.0;
    static final double DIRECTION_THRESHOLD = 1.0;

    private SensorMath() {
    }

    /** Unrounded results. Session peaks and swing detection use these, not the rounded metrics. */
    public record Result(
            double accelMagnitude,
            double angularVelocity,
            double batSpeedMps,
            double batSpeedKmh,
            double power,
            double backliftAngle,
            boolean impactDetected,
            String shotDirection) {
    }

    public static Result compute(RawReading r) {
        double accelMagnitude = Math.sqrt(r.ax() * r.ax() + r.ay() * r.ay() + r.az() * r.az());

        double angularVelocity = Math.sqrt(r.gx() * r.gx() + r.gy() * r.gy() + r.gz() * r.gz());
        if (angularVelocity < GYRO_DEAD_ZONE_DPS) {
            angularVelocity = 0;
        }

        // Tip speed v = angular velocity in rad/s x radius. The speed in m/s is not capped, only km/h.
        double batSpeedMps = angularVelocity * (Math.PI / 180) * BAT_RADIUS_M;
        double batSpeedKmh = Math.min(batSpeedMps * 3.6, MAX_SPEED_KMH);

        // Power = force x velocity, with force = mass x acceleration.
        double force = BAT_MASS_KG * accelMagnitude * GRAVITY;
        double power = force * batSpeedMps;

        double backliftAngle = Math.abs(r.gx()) * BACKLIFT_FACTOR;
        boolean impactDetected = accelMagnitude > IMPACT_THRESHOLD_G;

        String shotDirection;
        if (r.gz() > DIRECTION_THRESHOLD) {
            shotDirection = "leg";
        } else if (r.gz() < -DIRECTION_THRESHOLD) {
            shotDirection = "off";
        } else {
            shotDirection = "straight";
        }

        return new Result(accelMagnitude, angularVelocity, batSpeedMps, batSpeedKmh, power,
                backliftAngle, impactDetected, shotDirection);
    }
}
