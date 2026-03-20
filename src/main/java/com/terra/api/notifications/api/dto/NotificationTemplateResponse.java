package com.terra.api.notifications.api.dto;

import java.util.List;

public record NotificationTemplateResponse(
        String code,
        String category,
        String severity,
        String allowedTarget,
        String titleKey,
        String bodyKey,
        List<String> paramKeys
) {
}
