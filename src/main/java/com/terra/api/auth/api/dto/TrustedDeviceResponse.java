package com.terra.api.auth.api.dto;

import java.time.Instant;

public record TrustedDeviceResponse(
        Long sessionId,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant expiresAt,
        boolean currentSession
) {
}
