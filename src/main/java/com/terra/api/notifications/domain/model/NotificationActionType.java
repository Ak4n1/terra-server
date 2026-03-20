package com.terra.api.notifications.domain.model;

public enum NotificationActionType {
    ROUTE,
    EXTERNAL_URL;

    public String toWireValue() {
        return name().toLowerCase();
    }
}
