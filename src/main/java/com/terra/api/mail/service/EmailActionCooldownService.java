package com.terra.api.mail.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailActionCooldownService {

    private final Clock clock;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public EmailActionCooldownService() {
        this(Clock.systemUTC());
    }

    EmailActionCooldownService(Clock clock) {
        this.clock = clock;
    }

    public long getRetryAfterSeconds(String action, String email, long cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0L;
        }

        long nowMillis = clock.millis();
        long expiresAt = cooldowns.getOrDefault(buildKey(action, email), 0L);
        if (expiresAt <= nowMillis) {
            return 0L;
        }

        long remainingMillis = expiresAt - nowMillis;
        return Math.max(1L, (remainingMillis + 999L) / 1000L);
    }

    public void mark(String action, String email, long cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return;
        }

        long nowMillis = clock.millis();
        long expiresAt = nowMillis + cooldownSeconds * 1000L;
        cleanupExpired(nowMillis);
        cooldowns.put(buildKey(action, email), expiresAt);
    }

    private void cleanupExpired(long nowMillis) {
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
    }

    private String buildKey(String action, String email) {
        return action + ":" + email.trim().toLowerCase(Locale.ROOT);
    }
}
