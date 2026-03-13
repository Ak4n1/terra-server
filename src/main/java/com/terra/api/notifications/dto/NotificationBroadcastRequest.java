package com.terra.api.notifications.dto;

import java.util.Map;

public record NotificationBroadcastRequest(
        String template,
        Map<String, Object> params,
        String targetType,
        String targetValue
) {
}
