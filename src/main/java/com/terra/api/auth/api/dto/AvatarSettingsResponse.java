package com.terra.api.auth.api.dto;

public record AvatarSettingsResponse(
        String avatarType,
        String presetPath,
        String customUrl
) {
}

