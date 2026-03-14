package com.terra.api.notifications.dto;

public record NotificationAdminAuditSummaryResponse(
        long totalEntries,
        long uniqueRecipients,
        long uniqueTemplates,
        long unreadEntries
) {
}
