package com.terra.api.game.accounts.api.dto;

public record VerifyCreateCodeResponse(
        String verificationToken,
        long expiresInSeconds
) {
}

