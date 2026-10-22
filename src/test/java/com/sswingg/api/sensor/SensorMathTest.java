package com.sswingg.api.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sswingg.api.util.PyMath;
import org.junit.jupiter.api.Test;

/** Expected values were produced by the formulas in backend/app.py, run in Python 3.11. */
class SensorMathTest {

    @Test
    void ordinarySwing() {
        SensorMath.Result r = SensorMath.compute(new RawReading(2.0, 2.0, 1.5, -300, 1400, 80));
        assertEquals(49.55601268603458, r.batSpeedKmh(), 1e-9);
        assertEquals(13.765559079454048, r.batSpeedMps(), 1e-9);
        assertEquals(49.6, PyMath.round(r.batSpeedKmh(), 1));
        assertEquals(518.8, PyMath.round(r.power(), 1));
        assertEquals(3.2, PyMath.round(r.accelMagnitude(), 2));
        assertEquals(4500.0, r.backliftAngle());
        assertEquals(1434.02, PyMath.round(r.angularVelocity(), 2));
        assertEquals("leg", r.shotDirection());
        assertTrue(r.impactDetected());
    }

    @Test
    void speedIsCappedInKmhButPowerUsesTheUncappedSpeed() {
        SensorMath.Result r = SensorMath.compute(new RawReading(4.0, 4.0, 4.0, 3000, 3000, 3000));
        assertEquals(160.0, r.batSpeedKmh());
        assertEquals(49.879482516440994, r.batSpeedMps(), 1e-9);
        assertEquals(4068.1, PyMath.round(r.power(), 1));
        assertEquals(6.93, PyMath.round(r.accelMagnitude(), 2));
    }

    @Test
    void rotationBelowTheDeadZoneCountsAsNoSpeed() {
        SensorMath.Result below = SensorMath.compute(new RawReading(0, 0, 0, 5.9, 8, 0));
        assertEquals(0.0, below.angularVelocity());
        assertEquals(0.0, below.batSpeedKmh());
        assertEquals(88.5, below.backliftAngle());

        SensorMath.Result atThreshold = SensorMath.compute(new RawReading(0, 0, 0, 6, 8, 0));
        assertEquals(10.0, atThreshold.angularVelocity());
        assertEquals(0.3, PyMath.round(atThreshold.batSpeedKmh(), 1));
    }

    @Test
    void shotDirectionComesFromRotationAroundZ() {
        assertEquals("off", SensorMath.compute(new RawReading(1.5, 0.5, 0.25, 100, 200, -3)).shotDirection());
        assertEquals("leg", SensorMath.compute(new RawReading(0, 1, 0, 0, 0, 1.01)).shotDirection());
        assertEquals("straight", SensorMath.compute(new RawReading(0, 1, 0, 0, 0, 1.0)).shotDirection());
        assertEquals("straight", SensorMath.compute(new RawReading(0, 1, 0, 0, 0, -1.0)).shotDirection());
        assertFalse(SensorMath.compute(new RawReading(0, 3.0, 0, 0, 0, 0)).impactDetected());
    }
}
