package com.terra.api.notifications.admin;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.RoleName;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.common.exception.BadRequestException;
import com.terra.api.common.exception.ResourceNotFoundException;
import com.terra.api.notifications.dto.NotificationBroadcastRequest;
import com.terra.api.notifications.dto.NotificationBroadcastResponse;
import com.terra.api.notifications.dto.NotificationDispatchRequest;
import com.terra.api.notifications.dto.NotificationMutationResponse;
import com.terra.api.notifications.dto.NotificationTemplateResponse;
import com.terra.api.notifications.service.NotificationCommandService;
import com.terra.api.notifications.template.NotificationTemplateCode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationAdminService {

    private final AccountMasterRepository accountMasterRepository;
    private final NotificationCommandService notificationCommandService;

    public NotificationAdminService(AccountMasterRepository accountMasterRepository,
                                    NotificationCommandService notificationCommandService) {
        this.accountMasterRepository = accountMasterRepository;
        this.notificationCommandService = notificationCommandService;
    }

    public List<NotificationTemplateResponse> listAvailableTemplates() {
        return NotificationTemplateCode.adminTemplates().stream()
                .map(template -> new NotificationTemplateResponse(
                        template.getWireValue(),
                        template.getCategory().name(),
                        template.getSeverity().toWireValue(),
                        template.getAllowedTarget().name(),
                        template.getTitleKey(),
                        template.getBodyKey(),
                        template.getParamKeys()
                ))
                .toList();
    }

    public NotificationMutationResponse dispatch(NotificationDispatchRequest request) {
        String email = normalizeEmail(request.email());
        NotificationTemplateCode templateCode = resolveAdminTemplate(request.template());
        ensureTemplateAllowsIndividual(templateCode);
        AccountMaster account = accountMasterRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("notifications.admin_target_not_found"));

        return notificationCommandService.createFromTemplate(account, templateCode, sanitizeParams(templateCode, request.params()));
    }

    public NotificationBroadcastResponse broadcast(NotificationBroadcastRequest request) {
        NotificationTemplateCode templateCode = resolveAdminTemplate(request.template());
        ensureTemplateAllowsBroadcast(templateCode);
        NotificationBroadcastTargetType targetType = resolveTargetType(request.targetType());
        String targetValue = normalizeTargetValue(request.targetValue());
        Map<String, Object> params = sanitizeParams(templateCode, request.params());

        List<AccountMaster> accounts = switch (targetType) {
            case ROLE -> findAccountsByRole(targetValue);
            case SEGMENT -> findAccountsBySegment(targetValue);
        };

        accounts.forEach(account -> notificationCommandService.createFromTemplate(account, templateCode, params));
        return new NotificationBroadcastResponse(
                templateCode.getWireValue(),
                targetType.name(),
                targetValue,
                accounts.size()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("notifications.admin_email_required");
        }
        return email.trim();
    }

    private NotificationTemplateCode resolveAdminTemplate(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("notifications.admin_template_required");
        }

        NotificationTemplateCode templateCode;
        try {
            templateCode = NotificationTemplateCode.fromWireValue(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("notifications.admin_template_invalid");
        }

        if (!templateCode.isAdminEnabled()) {
            throw new BadRequestException("notifications.admin_template_not_allowed");
        }

        return templateCode;
    }

    private NotificationBroadcastTargetType resolveTargetType(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("notifications.admin_broadcast_target_type_required");
        }

        try {
            return NotificationBroadcastTargetType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("notifications.admin_broadcast_target_type_invalid");
        }
    }

    private String normalizeTargetValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("notifications.admin_broadcast_target_value_required");
        }
        return value.trim();
    }

    private List<AccountMaster> findAccountsByRole(String targetValue) {
        RoleName roleName;
        try {
            roleName = RoleName.valueOf(targetValue.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("notifications.admin_broadcast_role_invalid");
        }

        return accountMasterRepository.findDistinctByRoles_Name(roleName);
    }

    private List<AccountMaster> findAccountsBySegment(String targetValue) {
        NotificationAudienceSegment segment;
        try {
            segment = NotificationAudienceSegment.fromWireValue(targetValue);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("notifications.admin_broadcast_segment_invalid");
        }

        return switch (segment) {
            case ALL_ACTIVE -> accountMasterRepository.findAllEnabledAccounts();
            case EMAIL_VERIFIED -> accountMasterRepository.findByEnabledTrueAndEmailVerifiedTrue();
            case EMAIL_UNVERIFIED -> accountMasterRepository.findByEnabledTrueAndEmailVerifiedFalse();
        };
    }

    private void ensureTemplateAllowsIndividual(NotificationTemplateCode templateCode) {
        if (!templateCode.getAllowedTarget().allowsIndividual()) {
            throw new BadRequestException("notifications.admin_template_target_not_allowed");
        }
    }

    private void ensureTemplateAllowsBroadcast(NotificationTemplateCode templateCode) {
        if (!templateCode.getAllowedTarget().allowsBroadcast()) {
            throw new BadRequestException("notifications.admin_template_target_not_allowed");
        }
    }

    private Map<String, Object> sanitizeParams(NotificationTemplateCode templateCode, Map<String, Object> params) {
        Map<String, Object> source = params == null ? Map.of() : params;
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (String key : templateCode.getParamKeys()) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                sanitized.put(key, value);
            }
        }

        if (!source.keySet().stream().allMatch(templateCode.getParamKeys()::contains)) {
            throw new BadRequestException("notifications.admin_params_invalid");
        }

        return sanitized;
    }
}
