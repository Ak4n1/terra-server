package com.terra.api.realtime.websocket;

import com.terra.api.notifications.service.NotificationQueryService;
import com.terra.api.realtime.dto.RealtimeEventMessage;
import com.terra.api.realtime.dto.RealtimeEventType;
import com.terra.api.realtime.service.RealtimeEventPublisher;
import com.terra.api.realtime.session.RealtimeSessionService;
import com.terra.api.realtime.session.RealtimeSessionStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;
    private static final int MAX_CLIENT_MESSAGE_BYTES = 4 * 1024;

    private final ObjectMapper objectMapper;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final RealtimeSessionService realtimeSessionService;
    private final RealtimeHandshakeRateLimiter handshakeRateLimiter;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final NotificationQueryService notificationQueryService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public RealtimeWebSocketHandler(ObjectMapper objectMapper,
                                    RealtimeSessionRegistry realtimeSessionRegistry,
                                    RealtimeSessionService realtimeSessionService,
                                    RealtimeHandshakeRateLimiter handshakeRateLimiter,
                                    RealtimeEventPublisher realtimeEventPublisher,
                                    NotificationQueryService notificationQueryService) {
        this.objectMapper = objectMapper;
        this.realtimeSessionRegistry = realtimeSessionRegistry;
        this.realtimeSessionService = realtimeSessionService;
        this.handshakeRateLimiter = handshakeRateLimiter;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.notificationQueryService = notificationQueryService;
        startHeartbeat();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String realtimeSessionId = attribute(session, RealtimeHandshakeAttributes.REALTIME_SESSION_ID, String.class);
        Long accountId = attribute(session, RealtimeHandshakeAttributes.ACCOUNT_ID, Long.class);
        Long accountSessionId = attribute(session, RealtimeHandshakeAttributes.ACCOUNT_SESSION_ID, Long.class);
        if (realtimeSessionId == null || accountId == null || accountSessionId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        realtimeSessionRegistry.register(realtimeSessionId, accountId, accountSessionId, session);
        handshakeRateLimiter.incrementAccount(accountId);
        realtimeSessionService.touchSession(realtimeSessionId);
        realtimeEventPublisher.publishUnreadCount(accountId, Math.toIntExact(notificationQueryService.unreadCount(accountId)));
        try {
            sendEvent(session, RealtimeEventMessage.of(
                    RealtimeEventType.SYSTEM_CONNECTED,
                    Map.of(
                            "connectedAt", Instant.now().toString(),
                            "realtimeSessionId", realtimeSessionId
                    )
            ));
        } catch (IllegalStateException exception) {
            cleanup(session, "closed_during_connect");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > MAX_CLIENT_MESSAGE_BYTES) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }

        String realtimeSessionId = attribute(session, RealtimeHandshakeAttributes.REALTIME_SESSION_ID, String.class);
        if (realtimeSessionId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        realtimeSessionService.touchSession(realtimeSessionId);
        RealtimeEventMessage realtimeMessage = objectMapper.readValue(message.getPayload(), RealtimeEventMessage.class);
        RealtimeEventType type = realtimeMessage.getEventType();

        if (type == RealtimeEventType.SYSTEM_PONG || type == RealtimeEventType.NOTIFICATION_ACK) {
            return;
        }

        session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unsupported client event"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanup(session, status == null ? "closed" : status.getReason());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        cleanup(session, "transport_error");
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void closeAccountSessions(Long accountId, String reason) {
        realtimeSessionService.revokeActiveSessions(accountId, reason);
        realtimeEventPublisher.publishSessionRevoked(accountId, reason);
        realtimeSessionRegistry.closeAccountSessions(accountId);
    }

    public void closeAccountSession(Long accountSessionId, Long accountId, String reason) {
        realtimeSessionService.revokeActiveSessionsByAccountSession(accountSessionId, reason);
        realtimeEventPublisher.publishSessionRevoked(accountId, reason);
        realtimeSessionRegistry.closeAccountSessionSessions(accountSessionId);
    }

    private void cleanup(WebSocketSession session, String reason) {
        String realtimeSessionId = attribute(session, RealtimeHandshakeAttributes.REALTIME_SESSION_ID, String.class);
        Long accountId = attribute(session, RealtimeHandshakeAttributes.ACCOUNT_ID, Long.class);
        Long accountSessionId = attribute(session, RealtimeHandshakeAttributes.ACCOUNT_SESSION_ID, Long.class);
        if (realtimeSessionId != null) {
            realtimeSessionService.closeSession(realtimeSessionId, RealtimeSessionStatus.CLOSED, normalizeReason(reason));
        }
        if (realtimeSessionId != null && accountId != null && accountSessionId != null) {
            realtimeSessionRegistry.unregister(realtimeSessionId, accountId, accountSessionId);
            handshakeRateLimiter.decrementAccount(accountId);
        }
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<Long, java.util.Set<String>> entry : realtimeSessionRegistry.snapshotSessionsByAccount().entrySet()) {
                for (String realtimeSessionId : entry.getValue()) {
                    WebSocketSession session = realtimeSessionRegistry.getSession(realtimeSessionId);
                    if (session == null || !session.isOpen()) {
                        continue;
                    }

                    try {
                        sendEvent(session, RealtimeEventMessage.of(
                                RealtimeEventType.SYSTEM_PING,
                                Map.of("sentAt", Instant.now().toString())
                        ));
                    } catch (Exception ignored) {
                        cleanup(session, "heartbeat_failure");
                    }
                }
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void sendEvent(WebSocketSession session, RealtimeEventMessage message) throws Exception {
        if (!session.isOpen()) {
            throw new IllegalStateException("WebSocket session is closed");
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "closed";
        }
        return reason.length() > 128 ? reason.substring(0, 128) : reason;
    }

    @SuppressWarnings("unchecked")
    private <T> T attribute(WebSocketSession session, String attributeName, Class<T> type) {
        Object value = session.getAttributes().get(attributeName);
        if (value == null || !type.isInstance(value)) {
            return null;
        }
        return (T) value;
    }
}
