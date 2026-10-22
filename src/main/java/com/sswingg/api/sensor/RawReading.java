package com.sswingg.api.sensor;

/** One sensor sample. Acceleration is in g, rotation in degrees per second. */
public record RawReading(double ax, double ay, double az, double gx, double gy, double gz) {
}
