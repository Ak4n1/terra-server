package com.terra.api.auth.dto;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        boolean enabled,
        boolean emailVerified,
        Set<String> roles,
        Instant createdAt
) {
}
