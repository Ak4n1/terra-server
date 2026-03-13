package com.terra.api.notifications.template;

import com.terra.api.notifications.domain.NotificationCategory;
import com.terra.api.notifications.domain.NotificationSeverity;

import java.util.Arrays;
import java.util.List;

public enum NotificationTemplateCode {
    SYSTEM_TEST_NOTIFICATION(
            "system.test_notification",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BOTH,
            "notifications.systemTest.title",
            "notifications.systemTest.body",
            List.of(),
            true,
            true
    ),
    SYSTEM_MAINTENANCE_SCHEDULED(
            "system.maintenance_scheduled",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.maintenanceScheduled.title",
            "notifications.maintenanceScheduled.body",
            List.of("date", "time", "timezone"),
            true,
            false
    ),
    SYSTEM_SECURITY_REVIEW_REQUIRED(
            "system.security_review_required",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BOTH,
            "notifications.securityReviewRequired.title",
            "notifications.securityReviewRequired.body",
            List.of("reason"),
            true,
            false
    ),
    ADMIN_GENERAL_ANNOUNCEMENT(
            "admin.general_announcement",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BOTH,
            "notifications.adminGeneralAnnouncement.title",
            "notifications.adminGeneralAnnouncement.body",
            List.of("headline", "message"),
            true,
            false
    ),
    ADMIN_CONTACT_REQUEST(
            "admin.contact_request",
            NotificationCategory.ACCOUNT,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.INDIVIDUAL,
            "notifications.adminContactRequest.title",
            "notifications.adminContactRequest.body",
            List.of("message"),
            true,
            false
    ),
    ADMIN_ACCOUNT_REVIEW_REQUEST(
            "admin.account_review_request",
            NotificationCategory.ACCOUNT,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.INDIVIDUAL,
            "notifications.adminAccountReviewRequest.title",
            "notifications.adminAccountReviewRequest.body",
            List.of("message", "reason"),
            true,
            false
    ),
    ADMIN_SECURITY_NOTICE(
            "admin.security_notice",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BOTH,
            "notifications.adminSecurityNotice.title",
            "notifications.adminSecurityNotice.body",
            List.of("message", "actionLabel"),
            true,
            false
    ),
    ADMIN_MAINTENANCE_NOTICE(
            "admin.maintenance_notice",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.adminMaintenanceNotice.title",
            "notifications.adminMaintenanceNotice.body",
            List.of("date", "time", "message"),
            true,
            false
    ),
    ADMIN_SERVICE_ISSUE_NOTICE(
            "admin.service_issue_notice",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.adminServiceIssueNotice.title",
            "notifications.adminServiceIssueNotice.body",
            List.of("module", "message"),
            true,
            false
    ),
    ADMIN_SERVICE_RESTORED_NOTICE(
            "admin.service_restored_notice",
            NotificationCategory.SYSTEM,
            NotificationSeverity.SUCCESS,
            NotificationTemplateTarget.BROADCAST,
            "notifications.adminServiceRestoredNotice.title",
            "notifications.adminServiceRestoredNotice.body",
            List.of("module", "message"),
            true,
            false
    ),
    ADMIN_PAYMENT_STATUS_NOTICE(
            "admin.payment_status_notice",
            NotificationCategory.ACCOUNT,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.INDIVIDUAL,
            "notifications.adminPaymentStatusNotice.title",
            "notifications.adminPaymentStatusNotice.body",
            List.of("message"),
            true,
            false
    ),
    ADMIN_EVENT_INVITATION(
            "admin.event_invitation",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BOTH,
            "notifications.adminEventInvitation.title",
            "notifications.adminEventInvitation.body",
            List.of("eventName", "message"),
            true,
            false
    ),
    ADMIN_POLICY_UPDATE_NOTICE(
            "admin.policy_update_notice",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.adminPolicyUpdateNotice.title",
            "notifications.adminPolicyUpdateNotice.body",
            List.of("headline", "message"),
            true,
            false
    ),
    ACCOUNT_WELCOME_REGISTERED(
            "account.welcome_registered",
            NotificationCategory.ACCOUNT,
            NotificationSeverity.SUCCESS,
            NotificationTemplateTarget.INDIVIDUAL,
            "notifications.welcomeRegistered.title",
            "notifications.welcomeRegistered.body",
            List.of(),
            false,
            false
    );

    private final String wireValue;
    private final NotificationCategory category;
    private final NotificationSeverity severity;
    private final NotificationTemplateTarget allowedTarget;
    private final String titleKey;
    private final String bodyKey;
    private final List<String> paramKeys;
    private final boolean adminEnabled;
    private final boolean publicTestEnabled;

    NotificationTemplateCode(String wireValue,
                             NotificationCategory category,
                             NotificationSeverity severity,
                             NotificationTemplateTarget allowedTarget,
                             String titleKey,
                             String bodyKey,
                             List<String> paramKeys,
                             boolean adminEnabled,
                             boolean publicTestEnabled) {
        this.wireValue = wireValue;
        this.category = category;
        this.severity = severity;
        this.allowedTarget = allowedTarget;
        this.titleKey = titleKey;
        this.bodyKey = bodyKey;
        this.paramKeys = List.copyOf(paramKeys);
        this.adminEnabled = adminEnabled;
        this.publicTestEnabled = publicTestEnabled;
    }

    public String getWireValue() {
        return wireValue;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    public NotificationTemplateTarget getAllowedTarget() {
        return allowedTarget;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getBodyKey() {
        return bodyKey;
    }

    public List<String> getParamKeys() {
        return paramKeys;
    }

    public boolean isAdminEnabled() {
        return adminEnabled;
    }

    public boolean isPublicTestEnabled() {
        return publicTestEnabled;
    }

    public static NotificationTemplateCode fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(template -> template.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification template: " + value));
    }

    public static List<NotificationTemplateCode> adminTemplates() {
        return Arrays.stream(values())
                .filter(NotificationTemplateCode::isAdminEnabled)
                .toList();
    }
}
