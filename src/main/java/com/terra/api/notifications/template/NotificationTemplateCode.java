package com.terra.api.notifications.template;

import com.terra.api.notifications.domain.NotificationActionType;
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
            null,
            null,
            null,
            false,
            true
    ),
    SYSTEM_ADMIN_TEST_NOTIFICATION(
            "system.admin_test_notification",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BOTH,
            "notifications.adminTest.title",
            "notifications.adminTest.body",
            List.of(),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_MAINTENANCE_STARTS_AT(
            "system.maintenance_starts_at",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.maintenanceStartsAt.title",
            "notifications.maintenanceStartsAt.body",
            List.of("date", "time", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_MAINTENANCE_WINDOW(
            "system.maintenance_window",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.maintenanceWindow.title",
            "notifications.maintenanceWindow.body",
            List.of("startDate", "startTime", "endDate", "endTime", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_MAINTENANCE_ALL_DAY(
            "system.maintenance_all_day",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.maintenanceAllDay.title",
            "notifications.maintenanceAllDay.body",
            List.of("date", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_MAINTENANCE_EXTENDED_UNTIL(
            "system.maintenance_extended_until",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.maintenanceExtendedUntil.title",
            "notifications.maintenanceExtendedUntil.body",
            List.of("date", "time", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_MAINTENANCE_COMPLETED_AT(
            "system.maintenance_completed_at",
            NotificationCategory.SYSTEM,
            NotificationSeverity.SUCCESS,
            NotificationTemplateTarget.BROADCAST,
            "notifications.maintenanceCompletedAt.title",
            "notifications.maintenanceCompletedAt.body",
            List.of("date", "time", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_EMERGENCY_MAINTENANCE_UNTIL(
            "system.emergency_maintenance_until",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.emergencyMaintenanceUntil.title",
            "notifications.emergencyMaintenanceUntil.body",
            List.of("date", "time", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_RESTART_STARTS_AT(
            "system.restart_starts_at",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.restartStartsAt.title",
            "notifications.restartStartsAt.body",
            List.of("date", "time", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_SERVER_REOPENING_AT(
            "system.server_reopening_at",
            NotificationCategory.SYSTEM,
            NotificationSeverity.SUCCESS,
            NotificationTemplateTarget.BROADCAST,
            "notifications.serverReopeningAt.title",
            "notifications.serverReopeningAt.body",
            List.of("date", "time", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_EVENT_STARTS_AT(
            "system.event_starts_at",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.eventStartsAt.title",
            "notifications.eventStartsAt.body",
            List.of("eventName", "date", "time", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    SYSTEM_EVENT_ENDS_AT(
            "system.event_ends_at",
            NotificationCategory.SYSTEM,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.BROADCAST,
            "notifications.eventEndsAt.title",
            "notifications.eventEndsAt.body",
            List.of("eventName", "date", "time", "timezone"),
            null,
            null,
            null,
            true,
            false
    ),
    ACCOUNT_CONTACT_SUPPORT(
            "account.contact_support",
            NotificationCategory.ACCOUNT,
            NotificationSeverity.INFO,
            NotificationTemplateTarget.INDIVIDUAL,
            "notifications.contactSupport.title",
            "notifications.contactSupport.body",
            List.of("channelLabel", "url"),
            NotificationActionType.EXTERNAL_URL,
            "notifications.actions.openSupport",
            "url",
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
            null,
            null,
            null,
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
    private final NotificationActionType actionType;
    private final String actionLabelKey;
    private final String actionUrlParamKey;
    private final boolean adminEnabled;
    private final boolean publicTestEnabled;

    NotificationTemplateCode(String wireValue,
                             NotificationCategory category,
                             NotificationSeverity severity,
                             NotificationTemplateTarget allowedTarget,
                             String titleKey,
                             String bodyKey,
                             List<String> paramKeys,
                             NotificationActionType actionType,
                             String actionLabelKey,
                             String actionUrlParamKey,
                             boolean adminEnabled,
                             boolean publicTestEnabled) {
        this.wireValue = wireValue;
        this.category = category;
        this.severity = severity;
        this.allowedTarget = allowedTarget;
        this.titleKey = titleKey;
        this.bodyKey = bodyKey;
        this.paramKeys = List.copyOf(paramKeys);
        this.actionType = actionType;
        this.actionLabelKey = actionLabelKey;
        this.actionUrlParamKey = actionUrlParamKey;
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

    public NotificationActionType getActionType() {
        return actionType;
    }

    public String getActionLabelKey() {
        return actionLabelKey;
    }

    public String getActionUrlParamKey() {
        return actionUrlParamKey;
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
