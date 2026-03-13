package com.terra.api.notifications.domain;

public enum NotificationActionType {
    ROUTE;

    public String toWireValue() {
        return name().toLowerCase();
    }
}
