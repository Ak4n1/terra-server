package com.terra.api.notifications.api.dto;

public record NotificationBulkMutationResponse(
        long unreadCount,
        int updatedCount
) {
}
