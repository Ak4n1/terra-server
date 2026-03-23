package com.terra.api.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.profile")
public class ProfileSettingsProperties {

    private int usernameChangeCooldownDays = 14;

    public int getUsernameChangeCooldownDays() {
        return usernameChangeCooldownDays;
    }

    public void setUsernameChangeCooldownDays(int usernameChangeCooldownDays) {
        this.usernameChangeCooldownDays = usernameChangeCooldownDays;
    }
}
