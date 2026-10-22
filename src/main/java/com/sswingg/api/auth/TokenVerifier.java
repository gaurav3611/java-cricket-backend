package com.sswingg.api.auth;

import java.util.Optional;

public interface TokenVerifier {

    /** Returns the uid for a valid Firebase ID token, or empty when the token is invalid or expired. */
    Optional<String> verify(String idToken);
}
