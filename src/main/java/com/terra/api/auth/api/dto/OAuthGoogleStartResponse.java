package com.terra.api.auth.api.dto;

public record OAuthGoogleStartResponse(
        boolean requiresEmailCode,
        String challengeId,
        String maskedEmail,
        AuthSessionResponse session
) {
}
