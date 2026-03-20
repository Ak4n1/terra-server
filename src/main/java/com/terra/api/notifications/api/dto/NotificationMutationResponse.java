package com.terra.api.notifications.api.dto;

public record NotificationMutationResponse(
        NotificationResponse notification,
        long unreadCount
) {
}
