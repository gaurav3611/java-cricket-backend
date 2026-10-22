package com.sswingg.api.sensor;

/** lastSeen is the age of the device's latest reading in seconds. */
public record DeviceStatus(String deviceId, double lastSeen, boolean online) {
}
