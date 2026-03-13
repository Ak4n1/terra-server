package com.terra.api.notifications.dto;

import java.time.Instant;
import java.util.Map;

public record NotificationResponse(
        String id,
        String type,
        String category,
        String severity,
        String titleKey,
        String bodyKey,
        Map<String, Object> params,
        String status,
        NotificationActionResponse action,
        Instant occurredAt,
        Instant readAt
) {
}
