package com.terra.api.notifications.dto;

import java.util.Map;

public record NotificationActionResponse(
        String type,
        String labelKey,
        Map<String, Object> payload
) {
}
