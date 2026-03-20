package com.terra.api.notifications.api.dto;

public record NotificationBroadcastResponse(
        String template,
        String targetType,
        String targetValue,
        int deliveredCount
) {
}
