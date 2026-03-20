package com.terra.api.game.accounts.domain.model;

import java.time.Instant;

public record GameAccountCreationCode(
        Long id,
        Long accountId,
        String email,
        String codeHash,
        Instant expiresAt,
        int attemptCount,
        Instant verifiedAt,
        Instant consumedAt,
        String verificationTokenHash,
        Instant createdAt,
        Instant updatedAt
) {
    public GameAccountCreationCodeStatus status(Instant now, int maxAttempts) {
        if (consumedAt != null) {
            return GameAccountCreationCodeStatus.CONSUMED;
        }
        if (expiresAt != null && now.isAfter(expiresAt)) {
            return GameAccountCreationCodeStatus.EXPIRED;
        }
        if (attemptCount >= maxAttempts) {
            return GameAccountCreationCodeStatus.BLOCKED;
        }
        if (verifiedAt != null && verificationTokenHash != null && !verificationTokenHash.isBlank()) {
            return GameAccountCreationCodeStatus.VERIFIED;
        }
        return GameAccountCreationCodeStatus.PENDING;
    }
}

