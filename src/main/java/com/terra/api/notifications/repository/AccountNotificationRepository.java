package com.terra.api.notifications.repository;

import com.terra.api.notifications.domain.AccountNotification;
import com.terra.api.notifications.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AccountNotificationRepository extends JpaRepository<AccountNotification, Long> {
    Page<AccountNotification> findByAccount_IdOrderByOccurredAtDesc(Long accountId, Pageable pageable);

    Page<AccountNotification> findByAccount_IdAndStatusOrderByOccurredAtDesc(Long accountId, NotificationStatus status, Pageable pageable);

    long countByAccount_IdAndStatus(Long accountId, NotificationStatus status);

    Optional<AccountNotification> findByNotificationIdAndAccount_Id(String notificationId, Long accountId);

    Optional<AccountNotification> findByDedupeKey(String dedupeKey);

    @Modifying
    @Query("""
            update AccountNotification notification
               set notification.status = :readStatus,
                   notification.readAt = :readAt
             where notification.account.id = :accountId
               and notification.status = :unreadStatus
            """)
    int markAllReadByAccountId(@Param("accountId") Long accountId,
                               @Param("unreadStatus") NotificationStatus unreadStatus,
                               @Param("readStatus") NotificationStatus readStatus,
                               @Param("readAt") Instant readAt);
}
