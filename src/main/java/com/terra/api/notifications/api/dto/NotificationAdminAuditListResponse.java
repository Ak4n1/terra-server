package com.terra.api.notifications.api.dto;

import java.util.List;

public record NotificationAdminAuditListResponse(
        List<NotificationAdminAuditEntryResponse> items,
        NotificationAdminAuditSummaryResponse summary,
        int page,
        int size,
        boolean hasMore,
        long totalItems
) {
}
