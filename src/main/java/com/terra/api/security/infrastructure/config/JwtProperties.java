package com.terra.api.security.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private String issuer;
    private String audienceApi;
    private String audienceRealtime;
    private List<String> allowedAlgorithms = new ArrayList<>();
    private long accessTokenExpirationMinutes;
    private long refreshTokenExpirationDays;
    private String accessCookieName;
    private String refreshCookieName;
    private final Cookie cookie = new Cookie();

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudienceApi() {
        return audienceApi;
    }

    public void setAudienceApi(String audienceApi) {
        this.audienceApi = audienceApi;
    }

    public String getAudienceRealtime() {
        return audienceRealtime;
    }

    public void setAudienceRealtime(String audienceRealtime) {
        this.audienceRealtime = audienceRealtime;
    }

    public List<String> getAllowedAlgorithms() {
        return allowedAlgorithms;
    }

    public void setAllowedAlgorithms(List<String> allowedAlgorithms) {
        this.allowedAlgorithms = allowedAlgorithms;
    }

    public long getAccessTokenExpirationMinutes() {
        return accessTokenExpirationMinutes;
    }

    public void setAccessTokenExpirationMinutes(long accessTokenExpirationMinutes) {
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
    }

    public long getRefreshTokenExpirationDays() {
        return refreshTokenExpirationDays;
    }

    public void setRefreshTokenExpirationDays(long refreshTokenExpirationDays) {
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    public String getAccessCookieName() {
        return accessCookieName;
    }

    public void setAccessCookieName(String accessCookieName) {
        this.accessCookieName = accessCookieName;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public static class Cookie {
        private String sameSite;
        private boolean httpOnly;
        private boolean secure;
        private String path;

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public boolean isHttpOnly() {
            return httpOnly;
        }

        public void setHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
