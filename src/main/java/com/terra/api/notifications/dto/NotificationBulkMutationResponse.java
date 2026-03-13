package com.terra.api.notifications.dto;

public record NotificationBulkMutationResponse(
        long unreadCount,
        int updatedCount
) {
}
