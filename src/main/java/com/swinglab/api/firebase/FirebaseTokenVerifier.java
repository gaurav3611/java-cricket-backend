package com.swinglab.api.firebase;

import java.util.Optional;

import com.swinglab.api.auth.TokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FirebaseTokenVerifier implements TokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenVerifier.class);

    private final FirebaseContext firebase;

    public FirebaseTokenVerifier(FirebaseContext firebase) {
        this.firebase = firebase;
    }

    @Override
    public Optional<String> verify(String idToken) {
        try {
            return Optional.of(firebase.auth().verifyIdToken(idToken).getUid());
        } catch (Exception e) {
            log.warn("[AUTH] Token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
