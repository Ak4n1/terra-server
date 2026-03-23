package com.terra.api.game.accounts.api.dto;

import java.time.Instant;

public record GameAccountSummaryResponse(
        String login,
        String email,
        Instant createdAt,
        Instant lastActiveAt,
        int charactersCount,
        boolean hasNoCharacters
) {
}

