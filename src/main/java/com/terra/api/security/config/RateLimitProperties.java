package com.terra.api.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private final Policy authCritical = new Policy();
    private final Policy authSession = new Policy();
    private final Policy authSessionRead = new Policy();
    private final Policy authSessionRefresh = new Policy();

    public Policy getAuthCritical() {
        return authCritical;
    }

    public Policy getAuthSession() {
        return authSession;
    }

    public Policy getAuthSessionRead() {
        return authSessionRead;
    }

    public Policy getAuthSessionRefresh() {
        return authSessionRefresh;
    }

    public static class Policy {
        private boolean enabled;
        private int capacity;
        private int refillTokens;
        private long refillDurationSeconds;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        public long getRefillDurationSeconds() {
            return refillDurationSeconds;
        }

        public void setRefillDurationSeconds(long refillDurationSeconds) {
            this.refillDurationSeconds = refillDurationSeconds;
        }
    }
}
