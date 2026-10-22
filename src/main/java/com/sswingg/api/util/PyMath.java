package com.sswingg.api.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding that matches Python's built-in round(), which the Flask backend used.
 * Python rounds the exact binary value and breaks exact ties towards the even digit.
 * Math.round() rounds ties upwards, so it would give different numbers.
 */
public final class PyMath {

    private PyMath() {
    }

    /** Python round(value, digits). */
    public static double round(double value, int digits) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return new BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** Python round(value), which returns an integer. */
    public static long round(double value) {
        return (long) Math.rint(value);
    }
}
