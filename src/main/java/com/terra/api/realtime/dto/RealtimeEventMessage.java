package com.terra.api.realtime.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class RealtimeEventMessage {

    private String id = "evt_" + UUID.randomUUID();
    private String type;
    private int version = 1;
    private Instant occurredAt = Instant.now();
    private String traceId;
    private Map<String, Object> data;

    public static RealtimeEventMessage of(RealtimeEventType type, Map<String, Object> data) {
        RealtimeEventMessage message = new RealtimeEventMessage();
        message.setEventType(type);
        message.setData(data);
        return message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public RealtimeEventType getEventType() {
        return RealtimeEventType.fromWireValue(type);
    }

    public void setEventType(RealtimeEventType eventType) {
        this.type = eventType.getWireValue();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
