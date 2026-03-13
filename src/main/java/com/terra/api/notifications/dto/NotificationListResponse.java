package com.terra.api.notifications.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        long unreadCount,
        boolean hasMore,
        int page,
        int size
) {
}
