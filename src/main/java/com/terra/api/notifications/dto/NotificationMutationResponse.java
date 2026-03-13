package com.terra.api.notifications.dto;

public record NotificationMutationResponse(
        NotificationResponse notification,
        long unreadCount
) {
}
