package com.terra.api.notifications.service;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.notifications.domain.AccountNotification;
import com.terra.api.notifications.domain.NotificationStatus;
import com.terra.api.notifications.dto.NotificationBulkMutationResponse;
import com.terra.api.notifications.dto.NotificationMutationResponse;
import com.terra.api.notifications.dto.NotificationResponse;
import com.terra.api.notifications.mapper.NotificationMapper;
import com.terra.api.notifications.repository.AccountNotificationRepository;
import com.terra.api.notifications.template.NotificationTemplateCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class NotificationCommandService {

    private final AccountNotificationRepository accountNotificationRepository;
    private final NotificationQueryService notificationQueryService;
    private final NotificationMapper notificationMapper;
    private final NotificationRealtimePublisher notificationRealtimePublisher;

    public NotificationCommandService(AccountNotificationRepository accountNotificationRepository,
                                      NotificationQueryService notificationQueryService,
                                      NotificationMapper notificationMapper,
                                      NotificationRealtimePublisher notificationRealtimePublisher) {
        this.accountNotificationRepository = accountNotificationRepository;
        this.notificationQueryService = notificationQueryService;
        this.notificationMapper = notificationMapper;
        this.notificationRealtimePublisher = notificationRealtimePublisher;
    }

    @Transactional
    public NotificationMutationResponse createTestNotification(AccountMaster account) {
        return createFromTemplate(account, NotificationTemplateCode.SYSTEM_TEST_NOTIFICATION, Map.of());
    }

    @Transactional
    public NotificationMutationResponse createFromTemplate(AccountMaster account,
                                                           NotificationTemplateCode templateCode,
                                                           Map<String, Object> params) {
        AccountNotification notification = buildTemplateNotification(account, templateCode, params);
        return persistAndPublish(notification);
    }

    @Transactional
    public void createWelcomeRegistered(AccountMaster account) {
        String dedupeKey = "welcome_registered:" + account.getId();
        if (accountNotificationRepository.findByDedupeKey(dedupeKey).isPresent()) {
            return;
        }

        AccountNotification notification = buildTemplateNotification(account, NotificationTemplateCode.ACCOUNT_WELCOME_REGISTERED, Map.of());
        notification.setDedupeKey(dedupeKey);

        persistAndPublish(notification);
    }

    @Transactional
    public NotificationMutationResponse markRead(Long accountId, String notificationId) {
        AccountNotification notification = notificationQueryService.getOwnedNotification(accountId, notificationId);
        notification.markRead();
        AccountNotification saved = accountNotificationRepository.save(notification);
        return new NotificationMutationResponse(
                notificationMapper.toResponse(saved),
                notificationQueryService.unreadCount(accountId)
        );
    }

    @Transactional
    public NotificationBulkMutationResponse markAllRead(Long accountId) {
        int updatedCount = accountNotificationRepository.markAllReadByAccountId(
                accountId,
                NotificationStatus.UNREAD,
                NotificationStatus.READ,
                Instant.now()
        );

        return new NotificationBulkMutationResponse(
                notificationQueryService.unreadCount(accountId),
                updatedCount
        );
    }

    private NotificationMutationResponse persistAndPublish(AccountNotification notification) {
        AccountNotification saved = accountNotificationRepository.save(notification);
        NotificationResponse response = notificationMapper.toResponse(saved);
        long unreadCount = notificationQueryService.unreadCount(saved.getAccount().getId());
        notificationRealtimePublisher.publishCreated(saved.getAccount().getId(), response, unreadCount);
        return new NotificationMutationResponse(response, unreadCount);
    }

    private AccountNotification buildTemplateNotification(AccountMaster account,
                                                          NotificationTemplateCode templateCode,
                                                          Map<String, Object> params) {
        AccountNotification notification = new AccountNotification();
        notification.setAccount(account);
        notification.setType(templateCode.getWireValue());
        notification.setCategory(templateCode.getCategory());
        notification.setSeverity(templateCode.getSeverity());
        notification.setTitleKey(templateCode.getTitleKey());
        notification.setBodyKey(templateCode.getBodyKey());
        notification.setParamsJson(notificationMapper.writeMap(params == null ? Map.of() : params));
        return notification;
    }
}
