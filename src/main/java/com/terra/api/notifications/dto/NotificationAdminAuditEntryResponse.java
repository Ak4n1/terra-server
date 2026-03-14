package com.terra.api.notifications.dto;

public record NotificationAdminAuditEntryResponse(
        String notificationId,
        String recipientEmail,
        String template,
        String category,
        String severity,
        String status,
        String occurredAt
) {
}
