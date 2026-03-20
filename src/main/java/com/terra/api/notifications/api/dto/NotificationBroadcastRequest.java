package com.terra.api.notifications.api.dto;

import java.util.Map;

public record NotificationBroadcastRequest(
        String template,
        Map<String, Object> params,
        String targetType,
        String targetValue
) {
}
