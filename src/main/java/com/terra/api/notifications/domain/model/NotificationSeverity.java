package com.terra.api.notifications.domain.model;

public enum NotificationSeverity {
    INFO,
    SUCCESS;

    public String toWireValue() {
        return name().toLowerCase();
    }
}
