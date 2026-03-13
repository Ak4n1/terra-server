package com.terra.api.realtime.service;

import com.terra.api.realtime.dto.RealtimeEventMessage;
import com.terra.api.realtime.dto.RealtimeEventType;
import com.terra.api.realtime.websocket.RealtimeSessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class RealtimeEventPublisher {

    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final ObjectMapper objectMapper;

    public RealtimeEventPublisher(RealtimeSessionRegistry realtimeSessionRegistry,
                                  ObjectMapper objectMapper) {
        this.realtimeSessionRegistry = realtimeSessionRegistry;
        this.objectMapper = objectMapper;
    }

    public void publishToAccount(Long accountId, RealtimeEventMessage message) {
        String payload = serialize(message);
        if (payload == null) {
            return;
        }

        for (String realtimeSessionId : realtimeSessionRegistry.getSessionIdsForAccount(accountId)) {
            WebSocketSession session = realtimeSessionRegistry.getSession(realtimeSessionId);
            if (session == null || !session.isOpen()) {
                continue;
            }

            try {
                session.sendMessage(new TextMessage(payload));
            } catch (Exception ignored) {
                // Best effort delivery. Domain consistency stays on HTTP/DB.
            }
        }
    }

    public void publishUnreadCount(Long accountId, int unreadCount) {
        publishToAccount(accountId, RealtimeEventMessage.of(
                RealtimeEventType.NOTIFICATION_UNREAD_COUNT,
                Map.of("unreadCount", unreadCount)
        ));
    }

    public void publishSessionRevoked(Long accountId, String reason) {
        publishToAccount(accountId, RealtimeEventMessage.of(
                RealtimeEventType.ACCOUNT_SESSION_REVOKED,
                Map.of("reason", reason)
        ));
    }

    private String serialize(RealtimeEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception exception) {
            return null;
        }
    }
}
