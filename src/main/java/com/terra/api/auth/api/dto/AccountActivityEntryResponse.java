package com.terra.api.auth.api.dto;

import java.time.Instant;
import java.util.Map;

public record AccountActivityEntryResponse(
        String eventKey,
        Map<String, Object> metadata,
        String ipAddress,
        String userAgent,
        Instant occurredAt
) {
}
