package com.terra.api.notifications.service;

import com.terra.api.notifications.domain.AccountNotification;
import com.terra.api.notifications.domain.NotificationStatus;
import com.terra.api.notifications.dto.NotificationListResponse;
import com.terra.api.notifications.mapper.NotificationMapper;
import com.terra.api.notifications.repository.AccountNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                                               Boolean unreadOnly) {
        int limit = normalizeLimit(requestedLimit);
        int page = normalizePage(requestedPage);
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);
        Page<AccountNotification> notificationPage = onlyUnread
                ? accountNotificationRepository.findByAccount_IdAndStatusOrderByOccurredAtDesc(
                accountId,
                NotificationStatus.UNREAD,
                PageRequest.of(page, limit)
        )
                : accountNotificationRepository.findByAccount_IdOrderByOccurredAtDesc(accountId, PageRequest.of(page, limit));

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
                .orElseThrow(() -> new com.terra.api.common.exception.ResourceNotFoundException("notifications.notification_not_found"));
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
}
