package com.servicedesk.auth;

import com.servicedesk.user.UserResponse;
import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        long expiresInSeconds,
        UserResponse user) {

    /** Never print the token. */
    @Override
    public String toString() {
        return "LoginResponse[accessToken=****, expiresAt=" + expiresAt + ", user=" + user + "]";
    }
}