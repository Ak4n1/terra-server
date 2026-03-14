package com.terra.api.notifications.domain;

public enum NotificationActionType {
    ROUTE,
    EXTERNAL_URL;

    public String toWireValue() {
        return name().toLowerCase();
    }
}
