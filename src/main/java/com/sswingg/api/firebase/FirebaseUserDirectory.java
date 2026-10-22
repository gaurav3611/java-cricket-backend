package com.sswingg.api.firebase;

import java.util.Optional;

import com.google.firebase.auth.UserRecord;
import com.sswingg.api.profile.UserDirectory;
import com.sswingg.api.profile.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FirebaseUserDirectory implements UserDirectory {

    private static final Logger log = LoggerFactory.getLogger(FirebaseUserDirectory.class);

    private final FirebaseContext firebase;

    public FirebaseUserDirectory(FirebaseContext firebase) {
        this.firebase = firebase;
    }

    @Override
    public Optional<UserInfo> find(String uid) {
        try {
            UserRecord user = firebase.auth().getUser(uid);
            return Optional.of(new UserInfo(
                    user.getUid(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getPhotoUrl(),
                    user.getUserMetadata().getCreationTimestamp()));
        } catch (Exception e) {
            log.warn("[AUTH] Get user failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
