package com.terra.api.realtime.websocket;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealtimeHandshakeRateLimiter {

    private static final int MAX_CONNECTIONS_PER_IP_PER_MINUTE = 20;
    private static final int MAX_ACTIVE_CONNECTIONS_PER_ACCOUNT = 3;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, List<Long>> attemptsByIp = new ConcurrentHashMap<>();
    private final Map<Long, Integer> activeConnectionsByAccount = new ConcurrentHashMap<>();

    public boolean allowIp(String ipAddress) {
        long now = System.currentTimeMillis();
        List<Long> attempts = attemptsByIp.computeIfAbsent(ipAddress, key -> new ArrayList<>());
        synchronized (attempts) {
            attempts.removeIf(timestamp -> (now - timestamp) > WINDOW_MS);
            if (attempts.size() >= MAX_CONNECTIONS_PER_IP_PER_MINUTE) {
                return false;
            }
            attempts.add(now);
            return true;
        }
    }

    public boolean allowAccount(Long accountId) {
        return activeConnectionsByAccount.getOrDefault(accountId, 0) < MAX_ACTIVE_CONNECTIONS_PER_ACCOUNT;
    }

    public void incrementAccount(Long accountId) {
        activeConnectionsByAccount.merge(accountId, 1, Integer::sum);
    }

    public void decrementAccount(Long accountId) {
        activeConnectionsByAccount.computeIfPresent(accountId, (key, value) -> {
            int next = value - 1;
            return next > 0 ? next : null;
        });
    }

    public void resetForTests() {
        attemptsByIp.clear();
        activeConnectionsByAccount.clear();
    }
}
