package com.swinglab.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Expected values were produced by Python 3.11's round(). */
class PyMathTest {

    @Test
    void roundsToDigitsLikePython() {
        assertEquals(2.67, PyMath.round(2.675, 2));
        assertEquals(0.12, PyMath.round(0.125, 2));
        assertEquals(1.0, PyMath.round(1.005, 2));
        assertEquals(0.1, PyMath.round(0.05, 1));
        assertEquals(0.1, PyMath.round(0.15, 1));
        assertEquals(0.2, PyMath.round(0.25, 1));
        assertEquals(0.3, PyMath.round(0.35, 1));
        assertEquals(45.2, PyMath.round(45.25, 1));
        assertEquals(53.4, PyMath.round(53.449999, 1));
        assertEquals(160.0, PyMath.round(160.0, 1));
        assertEquals(-0.2, PyMath.round(-0.25, 1));
        assertEquals(2703.7, PyMath.round(2703.65, 1));
        assertEquals(0.0, PyMath.round(1e-9, 2));
    }

    @Test
    void roundsToIntegerLikePython() {
        assertEquals(0, PyMath.round(0.5));
        assertEquals(2, PyMath.round(1.5));
        assertEquals(2, PyMath.round(2.5));
        assertEquals(4, PyMath.round(3.5));
        assertEquals(0, PyMath.round(-0.5));
        assertEquals(-2, PyMath.round(-1.5));
        assertEquals(86, PyMath.round(86.5));
        assertEquals(88, PyMath.round(87.5));
        assertEquals(100, PyMath.round(99.999));
        assertEquals(3700, PyMath.round(3700.0000001));
    }

    @Test
    void formatsTimeLikePythonIsoformat() {
        java.time.Instant instant = java.time.Instant.parse("2026-10-10T05:01:09.884261Z");
        assertEquals("2026-10-10T05:01:09.884261+00:00", Timestamps.isoUtc(instant));
        assertEquals(1791608469.884261, Timestamps.epochSeconds(instant), 1e-6);
    }
}
