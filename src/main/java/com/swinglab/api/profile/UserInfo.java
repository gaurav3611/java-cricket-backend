package com.swinglab.api.profile;

/** A Firebase Authentication user. createdAt is in milliseconds since the epoch. */
public record UserInfo(String uid, String email, String displayName, String photoUrl, long createdAt) {
}
