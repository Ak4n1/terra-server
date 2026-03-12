package com.terra.api.security.service;

import com.terra.api.security.config.RateLimitProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimitService {

    private static final long STALE_BUCKET_MULTIPLIER = 3L;

    private final Map<String, BucketState> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicLong requestCounter = new AtomicLong();

    public RateLimitService() {
        this(Clock.systemUTC());
    }

    RateLimitService(Clock clock) {
        this.clock = clock;
    }

    public RateLimitResult tryConsume(String key, RateLimitProperties.Policy policy) {
        if (!policy.isEnabled()) {
            return RateLimitResult.allow();
        }

        int capacity = Math.max(1, policy.getCapacity());
        int refillTokens = Math.max(1, policy.getRefillTokens());
        long durationMillis = Math.max(1L, policy.getRefillDurationSeconds()) * 1000L;
        long now = clock.millis();

        RateLimitDecision decision = new RateLimitDecision();
        buckets.compute(key, (ignored, current) -> {
            BucketState state = current == null
                    ? new BucketState(capacity, now)
                    : current;

            state.refill(now, capacity, refillTokens, durationMillis);

            if (state.availableTokens >= 1d) {
                state.availableTokens -= 1d;
                state.lastTouchedAtMillis = now;
                decision.allow();
                return state;
            }

            state.lastTouchedAtMillis = now;
            decision.deny(state.retryAfterSeconds(refillTokens, durationMillis));
            return state;
        });

        if (requestCounter.incrementAndGet() % 256 == 0) {
            cleanupStaleBuckets(now, durationMillis);
        }

        return decision.toResult();
    }

    private void cleanupStaleBuckets(long now, long durationMillis) {
        long staleThreshold = durationMillis * STALE_BUCKET_MULTIPLIER;
        buckets.entrySet().removeIf(entry -> entry.getValue().isStale(now, staleThreshold));
    }

    private static final class BucketState {
        private double availableTokens;
        private long lastRefillAtMillis;
        private long lastTouchedAtMillis;

        private BucketState(int capacity, long now) {
            this.availableTokens = capacity;
            this.lastRefillAtMillis = now;
            this.lastTouchedAtMillis = now;
        }

        private void refill(long now, int capacity, int refillTokens, long durationMillis) {
            long elapsedMillis = Math.max(0L, now - lastRefillAtMillis);
            if (elapsedMillis == 0L) {
                return;
            }

            double tokensToAdd = (elapsedMillis / (double) durationMillis) * refillTokens;
            availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
            lastRefillAtMillis = now;
        }

        private long retryAfterSeconds(int refillTokens, long durationMillis) {
            double missingTokens = Math.max(0d, 1d - availableTokens);
            double millisPerToken = durationMillis / (double) refillTokens;
            long retryAfterMillis = (long) Math.ceil(missingTokens * millisPerToken);
            return Math.max(1L, (long) Math.ceil(retryAfterMillis / 1000d));
        }

        private boolean isStale(long now, long staleThresholdMillis) {
            return availableTokens >= 0.999d && (now - lastTouchedAtMillis) > staleThresholdMillis;
        }
    }

    private static final class RateLimitDecision {
        private boolean requestAllowed;
        private long retryAfterSeconds = 0L;

        private void allow() {
            this.requestAllowed = true;
            this.retryAfterSeconds = 0L;
        }

        private void deny(long retryAfterSeconds) {
            this.requestAllowed = false;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        private RateLimitResult toResult() {
            return requestAllowed
                    ? RateLimitResult.allow()
                    : RateLimitResult.deny(retryAfterSeconds);
        }
    }
}
