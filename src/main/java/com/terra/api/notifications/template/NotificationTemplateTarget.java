package com.terra.api.notifications.template;

public enum NotificationTemplateTarget {
    INDIVIDUAL,
    BROADCAST,
    BOTH;

    public boolean allowsIndividual() {
        return this == INDIVIDUAL || this == BOTH;
    }

    public boolean allowsBroadcast() {
        return this == BROADCAST || this == BOTH;
    }
}
