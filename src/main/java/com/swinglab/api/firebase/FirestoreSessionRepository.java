package com.swinglab.api.firebase;

import static com.swinglab.api.firebase.Futures.await;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.swinglab.api.session.SessionRepository;
import org.springframework.stereotype.Repository;

/** Firestore collection "sessions". The document id is the session id. */
@Repository
public class FirestoreSessionRepository implements SessionRepository {

    private static final String COLLECTION = "sessions";

    private final FirebaseContext firebase;

    public FirestoreSessionRepository(FirebaseContext firebase) {
        this.firebase = firebase;
    }

    private CollectionReference sessions() {
        return firebase.firestore().collection(COLLECTION);
    }

    @Override
    public void save(String sessionId, Map<String, Object> document) {
        await(sessions().document(sessionId).set(document));
    }

    @Override
    public List<Map<String, Object>> findRecentByUid(String uid, int limit) {
        return await(sessions()
                        .whereEqualTo("uid", uid)
                        .orderBy("started_at", Query.Direction.DESCENDING)
                        .limit(limit)
                        .get())
                .getDocuments().stream()
                .map(DocumentSnapshot::getData)
                .toList();
    }

    @Override
    public List<Map<String, Object>> findAllByUid(String uid) {
        return await(sessions().whereEqualTo("uid", uid).get())
                .getDocuments().stream()
                .map(DocumentSnapshot::getData)
                .toList();
    }

    @Override
    public Optional<Map<String, Object>> findById(String sessionId) {
        DocumentSnapshot snapshot = await(sessions().document(sessionId).get());
        return snapshot.exists() ? Optional.ofNullable(snapshot.getData()) : Optional.empty();
    }

    @Override
    public void delete(String sessionId) {
        await(sessions().document(sessionId).delete());
    }
}
