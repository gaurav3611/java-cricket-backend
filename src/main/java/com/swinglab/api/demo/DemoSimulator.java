package com.swinglab.api.demo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.swinglab.api.sensor.RawReading;
import com.swinglab.api.sensor.SensorService;
import com.swinglab.api.util.PyMath;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Feeds simulated readings for the device DEMO_BAT into the sensor pipeline at 50 Hz, so the
 * API can be tried without hardware. The swing pattern is the one from backend/app.py.
 *
 * Once started it runs until the application stops, as in the Flask app. Unlike the Flask
 * app, starting it again does not add a second simulation.
 */
@Component
public class DemoSimulator {

    public static final String DEVICE_ID = "DEMO_BAT";

    private static final Logger log = LoggerFactory.getLogger(DemoSimulator.class);
    private static final long TICK_MILLIS = 20;

    private final SensorService sensorService;
    private ScheduledExecutorService executor;
    private long tick;

    public DemoSimulator(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "demo-simulator");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::emit, 0, TICK_MILLIS, TimeUnit.MILLISECONDS);
        log.info("Demo simulation started for device {}", DEVICE_ID);
    }

    private void emit() {
        try {
            sensorService.ingest(DEVICE_ID, nextReading(tick++));
        } catch (RuntimeException e) {
            // An exception that escapes would cancel the schedule.
            log.warn("[ERR] Demo simulation tick failed: {}", e.getMessage());
        }
    }

    /** One step of a repeating 100-tick pattern: backlift, swing, follow-through and rest. */
    static RawReading nextReading(long t) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double phase = (t % 100) / 100.0;
        double ax, ay, az, gx, gy, gz;

        if (phase < 0.3) {
            gx = Math.sin(phase * 10) * 3;
            gy = 0.5;
            gz = random.nextDouble(-0.5, 0.5);
            ax = random.nextDouble(-0.3, 0.3);
            ay = random.nextDouble(0.8, 1.2);
            az = random.nextDouble(-0.3, 0.3);
        } else if (phase < 0.5) {
            double intensity = Math.sin((phase - 0.3) / 0.2 * Math.PI);
            gx = -intensity * 6;
            gy = intensity * 8;
            gz = (random.nextInt(3) - 1) * intensity * 2;
            ax = intensity * 4;
            ay = intensity * 3;
            az = intensity * 2;
        } else {
            double decay = Math.max(0, 1 - (phase - 0.5) * 4);
            gx = -decay * 2;
            gy = decay * 3;
            gz = random.nextDouble(-0.3, 0.3) * decay;
            ax = decay * random.nextDouble(0, 1);
            ay = 1 + decay * 0.5;
            az = decay * random.nextDouble(-0.5, 0.5);
        }

        return new RawReading(
                PyMath.round(ax, 2), PyMath.round(ay, 2), PyMath.round(az, 2),
                PyMath.round(gx, 2), PyMath.round(gy, 2), PyMath.round(gz, 2));
    }

    @PreDestroy
    synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
