package com.terra.api.auth.api.dto;

import java.time.Instant;

public record AccountProfileSummaryResponse(
        Integer totalAccounts,
        Instant lastLoginAt,
        Instant createdAt
) {
}
