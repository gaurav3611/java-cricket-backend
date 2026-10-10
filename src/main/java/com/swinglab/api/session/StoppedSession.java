package com.swinglab.api.session;

/** The result of stopping a session. analytics is null when no readings were recorded. */
public record StoppedSession(String sessionId, SessionAnalytics analytics) {
}
