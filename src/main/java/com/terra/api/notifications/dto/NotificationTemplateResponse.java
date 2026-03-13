package com.terra.api.notifications.dto;

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
