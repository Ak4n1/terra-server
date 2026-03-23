package com.terra.api.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.two-factor.recovery")
public class TwoFactorRecoveryProperties {

    private int requestCooldownMinutes = 2;

    public int getRequestCooldownMinutes() {
        return requestCooldownMinutes;
    }

    public void setRequestCooldownMinutes(int requestCooldownMinutes) {
        this.requestCooldownMinutes = requestCooldownMinutes;
    }
}
