package com.swinglab.api.sensor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.swinglab.api.session.SessionService;
import com.swinglab.api.util.PyMath;
import com.swinglab.api.util.Timestamps;
import org.springframework.stereotype.Service;

/**
 * Turns raw readings into metrics and remembers the latest reading of each device.
 * The buffer lives in memory, so it is lost on restart and is not shared between instances.
 */
@Service
public class SensorService {

    /** A device counts as online when its latest reading is newer than this many seconds. */
    private static final double ONLINE_WINDOW_SECONDS = 5.0;

    // Insertion order is kept so devices are listed in the order they were first seen.
    private final Map<String, ProcessedReading> latestByDevice = Collections.synchronizedMap(new LinkedHashMap<>());

    private final SessionService sessionService;

    public SensorService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public ProcessedReading ingest(String deviceId, RawReading raw) {
        SensorMath.Result result = SensorMath.compute(raw);
        ProcessedReading processed = new ProcessedReading(
                deviceId,
                Timestamps.epochSeconds(Instant.now()),
                raw,
                new SensorMetrics(
                        PyMath.round(result.batSpeedKmh(), 1),
                        PyMath.round(result.power(), 1),
                        PyMath.round(result.accelMagnitude(), 2),
                        PyMath.round(result.backliftAngle(), 1),
                        result.shotDirection(),
                        result.impactDetected(),
                        PyMath.round(result.angularVelocity(), 2)));

        latestByDevice.put(deviceId, processed);
        sessionService.accumulate(processed, result);
        return processed;
    }

    public DeviceStatusResponse deviceStatuses() {
        double now = Timestamps.epochSeconds(Instant.now());
        List<DeviceStatus> devices = new ArrayList<>();
        synchronized (latestByDevice) {
            for (ProcessedReading reading : latestByDevice.values()) {
                double age = now - reading.timestamp();
                devices.add(new DeviceStatus(reading.deviceId(), age, age < ONLINE_WINDOW_SECONDS));
            }
        }
        return new DeviceStatusResponse(devices, devices.size());
    }

    public int liveDeviceCount() {
        return latestByDevice.size();
    }
}
