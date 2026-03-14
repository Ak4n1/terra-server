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
import java.util.List;
import java.util.Optional;

public interface AccountNotificationRepository extends JpaRepository<AccountNotification, Long> {
    Page<AccountNotification> findByAccount_IdOrderByOccurredAtDesc(Long accountId, Pageable pageable);

    Page<AccountNotification> findByAccount_IdAndStatusOrderByOccurredAtDesc(Long accountId, NotificationStatus status, Pageable pageable);

    long countByAccount_IdAndStatus(Long accountId, NotificationStatus status);

    Optional<AccountNotification> findByNotificationIdAndAccount_Id(String notificationId, Long accountId);

    Optional<AccountNotification> findByDedupeKey(String dedupeKey);

    Page<AccountNotification> findByTypeInOrderByOccurredAtDesc(List<String> types, Pageable pageable);

    @Query(value = """
            select notification
              from AccountNotification notification
             where notification.type in :types
               and (:template is null or notification.type = :template)
               and (:status is null or notification.status = :status)
               and (:occurredFrom is null or notification.occurredAt >= :occurredFrom)
               and (:occurredTo is null or notification.occurredAt < :occurredTo)
             order by notification.occurredAt desc
            """,
            countQuery = """
            select count(notification)
              from AccountNotification notification
             where notification.type in :types
               and (:template is null or notification.type = :template)
               and (:status is null or notification.status = :status)
               and (:occurredFrom is null or notification.occurredAt >= :occurredFrom)
               and (:occurredTo is null or notification.occurredAt < :occurredTo)
            """)
    Page<AccountNotification> findAdminAuditEntries(@Param("types") List<String> types,
                                                    @Param("template") String template,
                                                    @Param("status") NotificationStatus status,
                                                    @Param("occurredFrom") Instant occurredFrom,
                                                    @Param("occurredTo") Instant occurredTo,
                                                    Pageable pageable);

    long countByTypeIn(List<String> types);

    long countByTypeInAndStatus(List<String> types, NotificationStatus status);

    @Query("""
            select count(notification)
              from AccountNotification notification
             where notification.type in :types
               and (:template is null or notification.type = :template)
               and (:status is null or notification.status = :status)
               and (:occurredFrom is null or notification.occurredAt >= :occurredFrom)
               and (:occurredTo is null or notification.occurredAt < :occurredTo)
            """)
    long countAdminAuditEntries(@Param("types") List<String> types,
                                @Param("template") String template,
                                @Param("status") NotificationStatus status,
                                @Param("occurredFrom") Instant occurredFrom,
                                @Param("occurredTo") Instant occurredTo);

    @Query("""
            select count(distinct notification.account.id)
              from AccountNotification notification
             where notification.type in :types
               and (:template is null or notification.type = :template)
               and (:status is null or notification.status = :status)
               and (:occurredFrom is null or notification.occurredAt >= :occurredFrom)
               and (:occurredTo is null or notification.occurredAt < :occurredTo)
            """)
    long countDistinctAccountIdsByTypeIn(@Param("types") List<String> types,
                                         @Param("template") String template,
                                         @Param("status") NotificationStatus status,
                                         @Param("occurredFrom") Instant occurredFrom,
                                         @Param("occurredTo") Instant occurredTo);

    @Query("""
            select count(distinct notification.type)
              from AccountNotification notification
             where notification.type in :types
               and (:template is null or notification.type = :template)
               and (:status is null or notification.status = :status)
               and (:occurredFrom is null or notification.occurredAt >= :occurredFrom)
               and (:occurredTo is null or notification.occurredAt < :occurredTo)
            """)
    long countDistinctTypesByTypeIn(@Param("types") List<String> types,
                                    @Param("template") String template,
                                    @Param("status") NotificationStatus status,
                                    @Param("occurredFrom") Instant occurredFrom,
                                    @Param("occurredTo") Instant occurredTo);

    @Query("""
            select count(notification)
              from AccountNotification notification
             where notification.type in :types
               and notification.status = :unreadStatus
               and (:template is null or notification.type = :template)
               and (:status is null or notification.status = :status)
               and (:occurredFrom is null or notification.occurredAt >= :occurredFrom)
               and (:occurredTo is null or notification.occurredAt < :occurredTo)
            """)
    long countAdminAuditUnreadEntries(@Param("types") List<String> types,
                                      @Param("template") String template,
                                      @Param("status") NotificationStatus status,
                                      @Param("occurredFrom") Instant occurredFrom,
                                      @Param("occurredTo") Instant occurredTo,
                                      @Param("unreadStatus") NotificationStatus unreadStatus);

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
