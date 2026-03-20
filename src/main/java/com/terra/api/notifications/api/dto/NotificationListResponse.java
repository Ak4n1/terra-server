package com.terra.api.notifications.api.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        long unreadCount,
        boolean hasMore,
        int page,
        int size
) {
}
