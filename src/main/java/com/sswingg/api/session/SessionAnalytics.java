package com.sswingg.api.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sswingg.api.sensor.ProcessedReading;
import com.sswingg.api.util.PyMath;
import com.sswingg.api.util.Timestamps;

/**
 * The end-of-session scores from the /api/session/stop route of backend/app.py, unchanged.
 * This includes its quirks: the sweet spot percentage is not capped at 100, and averages
 * are taken over the already-rounded per-reading metrics.
 */
public record SessionAnalytics(
        String sessionId,
        String uid,
        String deviceId,
        String startedAt,
        String endedAt,
        long durationSeconds,
        int totalReadings,
        int swingCount,
        double peakSpeedKmh,
        double avgSpeedKmh,
        double peakPowerWatts,
        double avgPowerWatts,
        double peakAccelG,
        long timingScore,
        long controlScore,
        long sweetSpotPct,
        long followThroughPct,
        int overallScore) {

    static SessionAnalytics compute(ActiveSession.Snapshot session, Instant endedAt) {
        List<ProcessedReading> points = session.dataPoints();
        int n = points.size();

        double[] speeds = new double[n];
        double speedSum = 0;
        double powerSum = 0;
        for (int i = 0; i < n; i++) {
            speeds[i] = points.get(i).metrics().speedKmh();
            speedSum += speeds[i];
            powerSum += points.get(i).metrics().powerWatts();
        }
        double avgSpeed = speedSum / n;
        double avgPower = powerSum / n;

        // Timing: how consistent the speed is while the bat is swinging.
        List<Double> swingSpeeds = new ArrayList<>();
        for (double speed : speeds) {
            if (speed > 20) {
                swingSpeeds.add(speed);
            }
        }
        double timingScore = swingSpeeds.size() > 1 ? clampScore(100 - standardDeviation(swingSpeeds) * 5) : 50;

        // Control: less variation in rotation around the z axis is better.
        List<Double> gzValues = new ArrayList<>();
        for (ProcessedReading point : points) {
            gzValues.add(point.raw().gz());
        }
        double controlScore = gzValues.size() > 1 ? clampScore(100 - standardDeviation(gzValues) * 10) : 50;

        // Sweet spot: readings with high acceleration at speed, relative to the number of swings.
        long sweetSpotHits = points.stream()
                .filter(p -> p.metrics().accelG() > 2.5 && p.metrics().speedKmh() > 20)
                .count();
        double sweetSpotPct = session.swingCount() > 0
                ? (double) sweetSpotHits / Math.max(1, session.swingCount()) * 100
                : 0;

        // Follow-through: speed kept over the readings right after the first peak.
        double followThrough = 75;
        if (n > 10) {
            int peakIndex = 0;
            for (int i = 1; i < n; i++) {
                if (speeds[i] > speeds[peakIndex]) {
                    peakIndex = i;
                }
            }
            int end = Math.min(peakIndex + 10, n);
            int length = end - peakIndex;
            if (length > 3) {
                double afterPeakSum = 0;
                for (int i = peakIndex + 1; i < end; i++) {
                    afterPeakSum += speeds[i];
                }
                followThrough = Math.min(100,
                        (afterPeakSum / (length - 1)) / Math.max(0.01, speeds[peakIndex]) * 100);
            }
        }

        int overallScore = (int) (timingScore * 0.3
                + controlScore * 0.25
                + Math.min(100, session.peakSpeed() * 1.2) * 0.25
                + Math.min(100, sweetSpotPct) * 0.2);

        double durationSeconds = Duration.between(session.startedAt(), endedAt).toNanos() / 1_000_000_000.0;

        return new SessionAnalytics(
                session.sessionId(),
                session.uid(),
                session.deviceId(),
                Timestamps.isoUtc(session.startedAt()),
                Timestamps.isoUtc(endedAt),
                PyMath.round(durationSeconds),
                n,
                session.swingCount(),
                PyMath.round(session.peakSpeed(), 1),
                PyMath.round(avgSpeed, 1),
                PyMath.round(session.peakPower(), 1),
                PyMath.round(avgPower, 1),
                PyMath.round(session.peakAccel(), 2),
                PyMath.round(timingScore),
                PyMath.round(controlScore),
                PyMath.round(sweetSpotPct),
                PyMath.round(followThrough),
                overallScore);
    }

    /** The Firestore document and the JSON sent to the client. Both use exactly these keys. */
    public Map<String, Object> toDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("session_id", sessionId);
        doc.put("uid", uid);
        doc.put("device_id", deviceId);
        doc.put("started_at", startedAt);
        doc.put("ended_at", endedAt);
        doc.put("duration_seconds", durationSeconds);
        doc.put("total_readings", totalReadings);
        doc.put("swing_count", swingCount);
        doc.put("peak_speed_kmh", peakSpeedKmh);
        doc.put("avg_speed_kmh", avgSpeedKmh);
        doc.put("peak_power_watts", peakPowerWatts);
        doc.put("avg_power_watts", avgPowerWatts);
        doc.put("peak_accel_g", peakAccelG);
        doc.put("timing_score", timingScore);
        doc.put("control_score", controlScore);
        doc.put("sweet_spot_pct", sweetSpotPct);
        doc.put("follow_through_pct", followThroughPct);
        doc.put("overall_score", overallScore);
        return doc;
    }

    private static double clampScore(double value) {
        return Math.max(0, Math.min(100, value));
    }

    /** Population standard deviation. */
    private static double standardDeviation(List<Double> values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        double mean = sum / values.size();
        double squares = 0;
        for (double value : values) {
            squares += (value - mean) * (value - mean);
        }
        return Math.sqrt(squares / values.size());
    }
}
