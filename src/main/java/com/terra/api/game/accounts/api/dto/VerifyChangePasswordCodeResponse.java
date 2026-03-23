package com.terra.api.game.accounts.api.dto;

public record VerifyChangePasswordCodeResponse(
        String verificationToken,
        long expiresInSeconds
) {
}

