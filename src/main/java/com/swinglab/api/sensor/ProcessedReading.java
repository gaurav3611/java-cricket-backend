package com.swinglab.api.sensor;

/** A reading together with its derived metrics. The timestamp is in seconds since the epoch. */
public record ProcessedReading(String deviceId, double timestamp, RawReading raw, SensorMetrics metrics) {
}
