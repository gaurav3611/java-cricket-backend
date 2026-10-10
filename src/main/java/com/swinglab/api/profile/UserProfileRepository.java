package com.swinglab.api.profile;

import java.util.Map;

public interface UserProfileRepository {

    /** Writes the given fields into the user's profile document and keeps its other fields. */
    void merge(String uid, Map<String, Object> fields);
}
