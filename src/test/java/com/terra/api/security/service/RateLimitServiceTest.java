package com.terra.api.security.service;

import com.terra.api.security.config.RateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    @Test
    void shouldAllowRequestsUntilCapacityAndThenDeny() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-12T00:00:00Z"));
        RateLimitService rateLimitService = new RateLimitService(clock);
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setEnabled(true);
        policy.setCapacity(2);
        policy.setRefillTokens(1);
        policy.setRefillDurationSeconds(60);

        assertTrue(rateLimitService.tryConsume("127.0.0.1:/api/auth/login", policy).requestAllowed());
        assertTrue(rateLimitService.tryConsume("127.0.0.1:/api/auth/login", policy).requestAllowed());

        RateLimitResult thirdResult = rateLimitService.tryConsume("127.0.0.1:/api/auth/login", policy);
        assertFalse(thirdResult.requestAllowed());
        assertEquals(60L, thirdResult.retryAfterSeconds());
    }

    @Test
    void shouldSkipLimitingWhenPolicyIsDisabled() {
        RateLimitService rateLimitService = new RateLimitService();
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setEnabled(false);

        assertTrue(rateLimitService.tryConsume("127.0.0.1:/api/auth/login", policy).requestAllowed());
    }

    @Test
    void shouldRefillTokensOverTimeAccordingToPolicy() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-12T00:00:00Z"));
        RateLimitService rateLimitService = new RateLimitService(clock);
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setEnabled(true);
        policy.setCapacity(3);
        policy.setRefillTokens(2);
        policy.setRefillDurationSeconds(60);

        assertTrue(rateLimitService.tryConsume("bucket", policy).requestAllowed());
        assertTrue(rateLimitService.tryConsume("bucket", policy).requestAllowed());
        assertTrue(rateLimitService.tryConsume("bucket", policy).requestAllowed());
        assertFalse(rateLimitService.tryConsume("bucket", policy).requestAllowed());

        clock.advanceSeconds(30);

        assertTrue(rateLimitService.tryConsume("bucket", policy).requestAllowed());
        assertFalse(rateLimitService.tryConsume("bucket", policy).requestAllowed());
    }

    private static final class MutableClock extends Clock {
        private Instant currentInstant;

        private MutableClock(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        private void advanceSeconds(long seconds) {
            currentInstant = currentInstant.plusSeconds(seconds);
        }
    }
}
