package com.terra.api.notifications.dto;

import java.util.Map;

public record NotificationDispatchRequest(
        String email,
        String template,
        Map<String, Object> params
) {
}
