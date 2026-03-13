package com.terra.api.notifications.admin;

import java.util.Arrays;

public enum NotificationAudienceSegment {
    ALL_ACTIVE("all_active"),
    EMAIL_VERIFIED("email_verified"),
    EMAIL_UNVERIFIED("email_unverified");

    private final String wireValue;

    NotificationAudienceSegment(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static NotificationAudienceSegment fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(segment -> segment.wireValue.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown audience segment: " + value));
    }
}
