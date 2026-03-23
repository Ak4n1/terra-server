package com.terra.api.auth.api.dto;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        String username,
        String avatarType,
        String avatarPresetPath,
        String avatarCustomUrl,
        boolean enabled,
        boolean emailVerified,
        String preferredLanguage,
        Set<String> roles,
        Instant createdAt
) {
}
