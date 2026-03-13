package com.terra.api.realtime.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealtimeSessionRegistry {

    private final Map<String, WebSocketSession> sessionsByRealtimeSessionId = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> sessionIdsByAccountId = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> sessionIdsByAccountSessionId = new ConcurrentHashMap<>();

    public void register(String realtimeSessionId, Long accountId, Long accountSessionId, WebSocketSession session) {
        sessionsByRealtimeSessionId.put(realtimeSessionId, session);
        sessionIdsByAccountId.computeIfAbsent(accountId, key -> ConcurrentHashMap.newKeySet()).add(realtimeSessionId);
        sessionIdsByAccountSessionId.computeIfAbsent(accountSessionId, key -> ConcurrentHashMap.newKeySet()).add(realtimeSessionId);
    }

    public void unregister(String realtimeSessionId, Long accountId, Long accountSessionId) {
        sessionsByRealtimeSessionId.remove(realtimeSessionId);
        sessionIdsByAccountId.computeIfPresent(accountId, (key, sessionIds) -> {
            sessionIds.remove(realtimeSessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
        });
        sessionIdsByAccountSessionId.computeIfPresent(accountSessionId, (key, sessionIds) -> {
            sessionIds.remove(realtimeSessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
        });
    }

    public Set<String> getSessionIdsForAccount(Long accountId) {
        return sessionIdsByAccountId.getOrDefault(accountId, Set.of());
    }

    public WebSocketSession getSession(String realtimeSessionId) {
        return sessionsByRealtimeSessionId.get(realtimeSessionId);
    }

    public Map<Long, Set<String>> snapshotSessionsByAccount() {
        Map<Long, Set<String>> snapshot = new java.util.HashMap<>();
        for (Map.Entry<Long, Set<String>> entry : sessionIdsByAccountId.entrySet()) {
            snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(snapshot);
    }

    public void closeAccountSessions(Long accountId) {
        for (String sessionId : getSessionIdsForAccount(accountId)) {
            WebSocketSession session = sessionsByRealtimeSessionId.get(sessionId);
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.close();
            } catch (IOException ignored) {
                // Best effort close during revocation.
            }
        }
    }

    public void closeAccountSessionSessions(Long accountSessionId) {
        for (String sessionId : sessionIdsByAccountSessionId.getOrDefault(accountSessionId, Set.of())) {
            WebSocketSession session = sessionsByRealtimeSessionId.get(sessionId);
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.close();
            } catch (IOException ignored) {
                // Best effort close during revocation.
            }
        }
    }
}
