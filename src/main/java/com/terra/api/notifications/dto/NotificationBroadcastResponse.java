package com.terra.api.notifications.dto;

public record NotificationBroadcastResponse(
        String template,
        String targetType,
        String targetValue,
        int deliveredCount
) {
}
