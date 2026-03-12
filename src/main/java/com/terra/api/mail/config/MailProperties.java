package com.terra.api.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private String from;
    private String fromName;
    private String frontendVerifyUrl;
    private String frontendResetPasswordUrl;
    private long verificationEmailCooldownSeconds = 60L;
    private long passwordResetCooldownSeconds = 60L;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getFrontendVerifyUrl() {
        return frontendVerifyUrl;
    }

    public void setFrontendVerifyUrl(String frontendVerifyUrl) {
        this.frontendVerifyUrl = frontendVerifyUrl;
    }

    public String getFrontendResetPasswordUrl() {
        return frontendResetPasswordUrl;
    }

    public void setFrontendResetPasswordUrl(String frontendResetPasswordUrl) {
        this.frontendResetPasswordUrl = frontendResetPasswordUrl;
    }

    public long getVerificationEmailCooldownSeconds() {
        return verificationEmailCooldownSeconds;
    }

    public void setVerificationEmailCooldownSeconds(long verificationEmailCooldownSeconds) {
        this.verificationEmailCooldownSeconds = verificationEmailCooldownSeconds;
    }

    public long getPasswordResetCooldownSeconds() {
        return passwordResetCooldownSeconds;
    }

    public void setPasswordResetCooldownSeconds(long passwordResetCooldownSeconds) {
        this.passwordResetCooldownSeconds = passwordResetCooldownSeconds;
    }
}
