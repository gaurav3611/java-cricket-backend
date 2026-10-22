package com.sswingg.api.profile;

import java.util.Optional;

public interface UserDirectory {

    /** Looks a user up in Firebase Authentication. Empty when the lookup fails. */
    Optional<UserInfo> find(String uid);
}
