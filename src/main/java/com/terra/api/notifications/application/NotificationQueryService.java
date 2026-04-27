package com.terra.api.notifications.application;

import com.terra.api.notifications.domain.model.AccountNotification;
import com.terra.api.notifications.domain.model.NotificationStatus;
import com.terra.api.notifications.api.dto.NotificationListResponse;
import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.notifications.infrastructure.mapping.NotificationMapper;
import com.terra.api.notifications.infrastructure.persistence.AccountNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class NotificationQueryService {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 50;

    private final AccountNotificationRepository accountNotificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationQueryService(AccountNotificationRepository accountNotificationRepository,
                                    NotificationMapper notificationMapper) {
        this.accountNotificationRepository = accountNotificationRepository;
        this.notificationMapper = notificationMapper;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse listLatest(Long accountId,
                                               Integer requestedLimit,
                                               Integer requestedPage,
                                               Boolean unreadOnly,
                                               String status,
                                               String sort,
                                               LocalDate dateFrom,
                                               LocalDate dateTo) {
        int limit = normalizeLimit(requestedLimit);
        int page = normalizePage(requestedPage);
        NotificationStatus effectiveStatus = resolveStatus(status, unreadOnly);
        Sort.Direction sortDirection = resolveSortDirection(sort);
        Instant occurredFrom = toOccurredFrom(dateFrom);
        Instant occurredTo = toOccurredToExclusive(dateTo);
        validateDateRange(occurredFrom, occurredTo);
        Page<AccountNotification> notificationPage = accountNotificationRepository.findUserEntries(
                accountId,
                effectiveStatus,
                occurredFrom,
                occurredTo,
                PageRequest.of(page, limit, Sort.by(sortDirection, "occurredAt"))
        );

        return new NotificationListResponse(
                notificationPage.getContent().stream()
                        .map(notificationMapper::toResponse)
                        .toList(),
                unreadCount(accountId),
                notificationPage.hasNext(),
                page,
                limit
        );
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long accountId) {
        return accountNotificationRepository.countByAccount_IdAndStatus(accountId, NotificationStatus.UNREAD);
    }

    @Transactional(readOnly = true)
    public AccountNotification getOwnedNotification(Long accountId, String notificationId) {
        return accountNotificationRepository.findByNotificationIdAndAccount_Id(notificationId, accountId)
                .orElseThrow(() -> new com.terra.api.common.domain.exception.ResourceNotFoundException("notifications.notification_not_found"));
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
    }

    private int normalizePage(Integer requestedPage) {
        if (requestedPage == null) {
            return 0;
        }
        return Math.max(0, requestedPage);
    }

    private NotificationStatus resolveStatus(String requestedStatus, Boolean unreadOnly) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return Boolean.TRUE.equals(unreadOnly) ? NotificationStatus.UNREAD : null;
        }

        String normalized = requestedStatus.trim().toUpperCase(Locale.ROOT);
        if ("UNREAD".equals(normalized)) {
            return NotificationStatus.UNREAD;
        }

        if ("READ".equals(normalized)) {
            return NotificationStatus.READ;
        }

        throw new BadRequestException("notifications.user_status_invalid");
    }

    private Sort.Direction resolveSortDirection(String requestedSort) {
        if (requestedSort == null || requestedSort.isBlank()) {
            return Sort.Direction.DESC;
        }

        String normalized = requestedSort.trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(normalized)) {
            return Sort.Direction.ASC;
        }

        if ("desc".equals(normalized)) {
            return Sort.Direction.DESC;
        }

        throw new BadRequestException("notifications.user_sort_invalid");
    }

    private Instant toOccurredFrom(LocalDate value) {
        if (value == null) {
            return null;
        }

        return value.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant toOccurredToExclusive(LocalDate value) {
        if (value == null) {
            return null;
        }

        return value.plusDays(1L).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private void validateDateRange(Instant occurredFrom, Instant occurredTo) {
        if (occurredFrom == null || occurredTo == null) {
            return;
        }

        if (!occurredFrom.isBefore(occurredTo)) {
            throw new BadRequestException("notifications.user_date_range_invalid");
        }
    }
}
