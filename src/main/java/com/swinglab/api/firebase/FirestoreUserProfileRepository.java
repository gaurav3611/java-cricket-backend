package com.swinglab.api.firebase;

import static com.swinglab.api.firebase.Futures.await;

import java.util.Map;

import com.google.cloud.firestore.SetOptions;
import com.swinglab.api.profile.UserProfileRepository;
import org.springframework.stereotype.Repository;

/** Firestore collection "users". The document id is the Firebase uid. */
@Repository
public class FirestoreUserProfileRepository implements UserProfileRepository {

    private final FirebaseContext firebase;

    public FirestoreUserProfileRepository(FirebaseContext firebase) {
        this.firebase = firebase;
    }

    @Override
    public void merge(String uid, Map<String, Object> fields) {
        await(firebase.firestore().collection("users").document(uid).set(fields, SetOptions.merge()));
    }
}
