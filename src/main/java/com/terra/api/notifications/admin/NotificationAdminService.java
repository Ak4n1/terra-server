package com.terra.api.notifications.admin;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.RoleName;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.common.exception.BadRequestException;
import com.terra.api.common.exception.ResourceNotFoundException;
import com.terra.api.notifications.domain.AccountNotification;
import com.terra.api.notifications.domain.NotificationStatus;
import com.terra.api.notifications.dto.NotificationAdminAuditEntryResponse;
import com.terra.api.notifications.dto.NotificationAdminAuditListResponse;
import com.terra.api.notifications.dto.NotificationAdminAuditSummaryResponse;
import com.terra.api.notifications.dto.NotificationBroadcastRequest;
import com.terra.api.notifications.dto.NotificationBroadcastResponse;
import com.terra.api.notifications.dto.NotificationDispatchRequest;
import com.terra.api.notifications.dto.NotificationMutationResponse;
import com.terra.api.notifications.dto.NotificationTemplateResponse;
import com.terra.api.notifications.repository.AccountNotificationRepository;
import com.terra.api.notifications.service.NotificationCommandService;
import com.terra.api.notifications.template.NotificationTemplateCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationAdminService {
    private static final int DEFAULT_AUDIT_SIZE = 4;
    private static final int MAX_AUDIT_SIZE = 20;

    private final AccountMasterRepository accountMasterRepository;
    private final AccountNotificationRepository accountNotificationRepository;
    private final NotificationCommandService notificationCommandService;

    public NotificationAdminService(AccountMasterRepository accountMasterRepository,
                                    AccountNotificationRepository accountNotificationRepository,
                                    NotificationCommandService notificationCommandService) {
        this.accountMasterRepository = accountMasterRepository;
        this.accountNotificationRepository = accountNotificationRepository;
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

    @Transactional(readOnly = true)
    public NotificationAdminAuditListResponse listAudit(int page, int size, String template, String status, String dateFrom, String dateTo) {
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.min(Math.max(size, 1), MAX_AUDIT_SIZE);
        if (size <= 0) {
            resolvedSize = DEFAULT_AUDIT_SIZE;
        }

        List<String> templateCodes = NotificationTemplateCode.adminTemplates().stream()
                .map(NotificationTemplateCode::getWireValue)
                .toList();

        String resolvedTemplate = normalizeAuditTemplate(template, templateCodes);
        NotificationStatus resolvedStatus = normalizeAuditStatus(status);
        Instant occurredFrom = parseAuditDateStart(dateFrom);
        Instant occurredTo = parseAuditDateExclusiveEnd(dateTo);
        ensureAuditDateRange(occurredFrom, occurredTo);

        Page<AccountNotification> notificationPage = accountNotificationRepository.findAdminAuditEntries(
                templateCodes,
                resolvedTemplate,
                resolvedStatus,
                occurredFrom,
                occurredTo,
                PageRequest.of(resolvedPage, resolvedSize)
        );

        List<NotificationAdminAuditEntryResponse> items = notificationPage.getContent().stream()
                .map(this::toAuditEntry)
                .toList();

        NotificationAdminAuditSummaryResponse summary = new NotificationAdminAuditSummaryResponse(
                accountNotificationRepository.countAdminAuditEntries(templateCodes, resolvedTemplate, resolvedStatus, occurredFrom, occurredTo),
                accountNotificationRepository.countDistinctAccountIdsByTypeIn(templateCodes, resolvedTemplate, resolvedStatus, occurredFrom, occurredTo),
                accountNotificationRepository.countDistinctTypesByTypeIn(templateCodes, resolvedTemplate, resolvedStatus, occurredFrom, occurredTo),
                accountNotificationRepository.countAdminAuditUnreadEntries(templateCodes, resolvedTemplate, resolvedStatus, occurredFrom, occurredTo, NotificationStatus.UNREAD)
        );

        return new NotificationAdminAuditListResponse(
                items,
                summary,
                notificationPage.getNumber(),
                notificationPage.getSize(),
                notificationPage.hasNext(),
                notificationPage.getTotalElements()
        );
    }

    private String normalizeAuditTemplate(String template, List<String> templateCodes) {
        if (template == null || template.isBlank()) {
            return null;
        }

        String normalized = template.trim();
        if (!templateCodes.contains(normalized)) {
            throw new BadRequestException("notifications.admin_audit_template_invalid");
        }

        return normalized;
    }

    private NotificationStatus normalizeAuditStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return NotificationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("notifications.admin_audit_status_invalid");
        }
    }

    private Instant parseAuditDateStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value.trim()).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("notifications.admin_audit_date_invalid");
        }
    }

    private Instant parseAuditDateExclusiveEnd(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value.trim()).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("notifications.admin_audit_date_invalid");
        }
    }

    private void ensureAuditDateRange(Instant occurredFrom, Instant occurredTo) {
        if (occurredFrom != null && occurredTo != null && !occurredFrom.isBefore(occurredTo)) {
            throw new BadRequestException("notifications.admin_audit_date_range_invalid");
        }
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

        validateUrlParam(templateCode, sanitized);

        return sanitized;
    }

    private void validateUrlParam(NotificationTemplateCode templateCode, Map<String, Object> sanitized) {
        String urlParamKey = templateCode.getActionUrlParamKey();
        if (urlParamKey == null) {
            return;
        }

        Object value = sanitized.get(urlParamKey);
        if (!(value instanceof String url) || !isSupportedExternalUrl(url)) {
            throw new BadRequestException("notifications.admin_url_invalid");
        }
    }

    private boolean isSupportedExternalUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private NotificationAdminAuditEntryResponse toAuditEntry(AccountNotification notification) {
        return new NotificationAdminAuditEntryResponse(
                notification.getNotificationId(),
                notification.getAccount().getEmail(),
                notification.getType(),
                notification.getCategory().name(),
                notification.getSeverity().toWireValue(),
                notification.getStatus().name(),
                DateTimeFormatter.ISO_INSTANT.format(notification.getOccurredAt())
        );
    }
}
