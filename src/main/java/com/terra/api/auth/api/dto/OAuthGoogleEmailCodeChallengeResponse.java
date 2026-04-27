package com.terra.api.auth.api.dto;

public record OAuthGoogleEmailCodeChallengeResponse(
        String challengeId,
        String maskedEmail
) {
}
