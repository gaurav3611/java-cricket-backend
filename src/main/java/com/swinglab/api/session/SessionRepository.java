package com.swinglab.api.session;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stored session analytics. Each document carries the owner's Firebase uid in the field "uid". */
public interface SessionRepository {

    void save(String sessionId, Map<String, Object> document);

    /** The newest sessions of a user, ordered by "started_at" descending. */
    List<Map<String, Object>> findRecentByUid(String uid, int limit);

    List<Map<String, Object>> findAllByUid(String uid);

    Optional<Map<String, Object>> findById(String sessionId);

    void delete(String sessionId);
}
