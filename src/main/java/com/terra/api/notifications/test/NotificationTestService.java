package com.terra.api.notifications.test;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.common.exception.BadRequestException;
import com.terra.api.common.exception.ResourceNotFoundException;
import com.terra.api.notifications.dto.NotificationDispatchRequest;
import com.terra.api.notifications.dto.NotificationMutationResponse;
import com.terra.api.notifications.service.NotificationCommandService;
import com.terra.api.notifications.template.NotificationTemplateCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Profile({"dev", "test"})
@Service
public class NotificationTestService {

    private final AccountMasterRepository accountMasterRepository;
    private final NotificationCommandService notificationCommandService;

    public NotificationTestService(AccountMasterRepository accountMasterRepository,
                                   NotificationCommandService notificationCommandService) {
        this.accountMasterRepository = accountMasterRepository;
        this.notificationCommandService = notificationCommandService;
    }

    public NotificationMutationResponse dispatch(NotificationDispatchRequest request) {
        String email = normalizeEmail(request.email());
        NotificationTemplateCode templateCode = resolveAllowedTemplate(request.template());
        AccountMaster account = accountMasterRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("notifications.test_target_not_found"));

        return notificationCommandService.createFromTemplate(account, templateCode, sanitizeParams(request.params()));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("notifications.test_email_required");
        }
        return email.trim();
    }

    private NotificationTemplateCode resolveAllowedTemplate(String value) {
        String templateValue = (value == null || value.isBlank())
                ? NotificationTemplateCode.SYSTEM_TEST_NOTIFICATION.getWireValue()
                : value.trim();

        NotificationTemplateCode templateCode;
        try {
            templateCode = NotificationTemplateCode.fromWireValue(templateValue);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("notifications.test_template_invalid");
        }

        if (!templateCode.isPublicTestEnabled()) {
            throw new BadRequestException("notifications.test_template_not_allowed");
        }

        return templateCode;
    }

    private Map<String, Object> sanitizeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                sanitized.put(entry.getKey(), value);
            }
        }
        return sanitized;
    }
}
