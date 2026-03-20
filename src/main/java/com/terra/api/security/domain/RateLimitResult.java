package com.terra.api.security.domain;

public record RateLimitResult(
        boolean requestAllowed,
        long retryAfterSeconds
) {
    public static RateLimitResult allow() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult deny(long retryAfterSeconds) {
        return new RateLimitResult(false, retryAfterSeconds);
    }
}
