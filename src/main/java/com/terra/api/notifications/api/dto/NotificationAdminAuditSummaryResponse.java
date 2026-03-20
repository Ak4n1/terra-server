package com.terra.api.notifications.api.dto;

public record NotificationAdminAuditSummaryResponse(
        long totalEntries,
        long uniqueRecipients,
        long uniqueTemplates,
        long unreadEntries
) {
}
