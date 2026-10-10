package com.swinglab.api.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Time values in the formats the Flask backend produced. */
public final class Timestamps {

    // Same text as Python's datetime.now(timezone.utc).isoformat(), for example
    // 2026-10-10T05:01:09.884261+00:00. Existing Firestore documents use this format
    // and are sorted by it, so new documents must match.
    private static final DateTimeFormatter PYTHON_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private Timestamps() {
    }

    public static String isoUtc(Instant instant) {
        return PYTHON_ISO.format(instant.atOffset(ZoneOffset.UTC));
    }

    /** Seconds since the epoch with a fraction, like Python's time.time(). */
    public static double epochSeconds(Instant instant) {
        return instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;
    }
}
