package com.terra.api.realtime.dto;

public enum RealtimeEventType {
    SYSTEM_CONNECTED("system.connected"),
    SYSTEM_PING("system.ping"),
    SYSTEM_PONG("system.pong"),
    SYSTEM_REFRESH_REQUIRED("system.refresh_required"),
    SYSTEM_SHUTDOWN("system.shutdown"),
    ACCOUNT_SESSION_REVOKED("account.session.revoked"),
    NOTIFICATION_CREATED("notification.created"),
    NOTIFICATION_UNREAD_COUNT("notification.unread_count"),
    NOTIFICATION_ACK("notification.ack");

    private final String wireValue;

    RealtimeEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static RealtimeEventType fromWireValue(String wireValue) {
        for (RealtimeEventType type : values()) {
            if (type.wireValue.equals(wireValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported realtime event type: " + wireValue);
    }
}
