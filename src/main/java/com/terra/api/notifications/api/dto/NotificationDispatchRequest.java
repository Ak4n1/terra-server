package com.terra.api.notifications.api.dto;

import java.util.Map;

public record NotificationDispatchRequest(
        String email,
        String template,
        Map<String, Object> params
) {
}
