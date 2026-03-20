package com.terra.api.notifications.infrastructure.realtime;

import com.terra.api.notifications.api.dto.NotificationResponse;
import com.terra.api.realtime.api.dto.RealtimeEventMessage;
import com.terra.api.realtime.api.dto.RealtimeEventType;
import com.terra.api.realtime.application.RealtimeEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Service
public class NotificationRealtimePublisher {

    private final RealtimeEventPublisher realtimeEventPublisher;

    public NotificationRealtimePublisher(RealtimeEventPublisher realtimeEventPublisher) {
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    public void publishCreated(Long accountId, NotificationResponse notification, long unreadCount) {
        Runnable publishAction = () -> {
            realtimeEventPublisher.publishToAccount(accountId, RealtimeEventMessage.of(
                    RealtimeEventType.NOTIFICATION_CREATED,
                    Map.of(
                            "notification", notification,
                            "unreadCount", unreadCount
                    )
            ));
            realtimeEventPublisher.publishUnreadCount(accountId, Math.toIntExact(unreadCount));
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAction.run();
                }
            });
            return;
        }

        publishAction.run();
    }
}
