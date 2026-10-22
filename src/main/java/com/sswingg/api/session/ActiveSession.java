package com.sswingg.api.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.sswingg.api.sensor.ProcessedReading;
import com.sswingg.api.sensor.SensorMath;

/** A recording that is in progress. Readings arrive on other threads, so access is synchronized. */
final class ActiveSession {

    /** A swing is counted when speed rises above this value in km/h... */
    private static final double SWING_START_KMH = 25.0;
    /** ...and the previous reading was below this value in km/h. */
    private static final double SWING_REST_KMH = 15.0;

    private final String sessionId;
    private final String uid;
    private final String deviceId;
    private final Instant startedAt;

    private final List<ProcessedReading> dataPoints = new ArrayList<>();
    private int swingCount;
    private double peakSpeed;
    private double peakPower;
    private double peakAccel;
    private double lastSpeed;

    ActiveSession(String sessionId, String uid, String deviceId, Instant startedAt) {
        this.sessionId = sessionId;
        this.uid = uid;
        this.deviceId = deviceId;
        this.startedAt = startedAt;
    }

    String deviceId() {
        return deviceId;
    }

    synchronized void add(ProcessedReading reading, SensorMath.Result result) {
        dataPoints.add(reading);
        peakSpeed = Math.max(peakSpeed, result.batSpeedKmh());
        peakPower = Math.max(peakPower, result.power());
        peakAccel = Math.max(peakAccel, result.accelMagnitude());

        if (result.batSpeedKmh() > SWING_START_KMH && lastSpeed < SWING_REST_KMH) {
            swingCount++;
        }
        lastSpeed = result.batSpeedKmh();
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(sessionId, uid, deviceId, startedAt, List.copyOf(dataPoints),
                swingCount, peakSpeed, peakPower, peakAccel);
    }

    record Snapshot(
            String sessionId,
            String uid,
            String deviceId,
            Instant startedAt,
            List<ProcessedReading> dataPoints,
            int swingCount,
            double peakSpeed,
            double peakPower,
            double peakAccel) {
    }
}
