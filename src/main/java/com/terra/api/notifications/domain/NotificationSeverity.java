package com.terra.api.notifications.domain;

public enum NotificationSeverity {
    INFO,
    SUCCESS;

    public String toWireValue() {
        return name().toLowerCase();
    }
}
