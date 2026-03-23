package com.terra.api.auth.api.dto;

import java.time.Instant;

public record AccountSecurityStatusResponse(
        boolean twoFactorEnabled,
        Instant twoFactorEnabledAt,
        Instant twoFactorRecoveryRequestedAt
) {
}
