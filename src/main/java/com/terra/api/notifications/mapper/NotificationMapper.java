package com.terra.api.notifications.mapper;

import com.terra.api.notifications.domain.AccountNotification;
import com.terra.api.notifications.dto.NotificationActionResponse;
import com.terra.api.notifications.dto.NotificationResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NotificationMapper {

    private final ObjectMapper objectMapper;

    public NotificationMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NotificationResponse toResponse(AccountNotification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getType(),
                notification.getCategory().name(),
                notification.getSeverity().toWireValue(),
                notification.getTitleKey(),
                notification.getBodyKey(),
                parseMap(notification.getParamsJson()),
                notification.getStatus().name(),
                toAction(notification),
                notification.getOccurredAt(),
                notification.getReadAt()
        );
    }

    public String writeMap(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(value, LinkedHashMap.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private NotificationActionResponse toAction(AccountNotification notification) {
        if (notification.getActionType() == null) {
            return null;
        }
        return new NotificationActionResponse(
                notification.getActionType().toWireValue(),
                notification.getActionLabelKey(),
                parseMap(notification.getActionPayloadJson())
        );
    }
}
