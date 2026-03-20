package com.terra.api.game.accounts.api.dto;

import java.time.Instant;

public record CreateGameAccountResponse(
        String accountName,
        String email,
        Instant createdAt
) {
}

