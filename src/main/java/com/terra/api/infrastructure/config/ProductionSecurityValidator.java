package com.terra.api.infrastructure.config;

import com.terra.api.security.infrastructure.config.CsrfProperties;
import com.terra.api.security.infrastructure.config.JwtProperties;
import com.terra.api.mail.infrastructure.config.MailProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {

    private static final String DEFAULT_JWT_SECRET = "4d2a9f16b1c4e8870f52c93d7a6e14fb5c20a8d31e74f9b6c2d11a5e9f08bc47";
    private static final String BETA_DOMAIN = "beta.l2terra.online";

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final CsrfProperties csrfProperties;
    private final CorsProperties corsProperties;
    private final MailProperties mailProperties;

    public ProductionSecurityValidator(Environment environment,
                                       JwtProperties jwtProperties,
                                       CsrfProperties csrfProperties,
                                       CorsProperties corsProperties,
                                       MailProperties mailProperties) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.csrfProperties = csrfProperties;
        this.corsProperties = corsProperties;
        this.mailProperties = mailProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.matchesProfiles("prod")) {
            return;
        }

        validateJwtSecret();
        validateJwtClaimsPolicy();
        validateCookieSecurity();
        validateCorsOrigins();
        validateMailFrontendUrls();
    }

    private void validateJwtSecret() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank() || DEFAULT_JWT_SECRET.equals(secret)) {
            throw new IllegalStateException("Production requires a strong JWT secret configured outside default values.");
        }
    }

    private void validateJwtClaimsPolicy() {
        if (jwtProperties.getIssuer() == null || jwtProperties.getIssuer().isBlank()) {
            throw new IllegalStateException("Production requires jwt.issuer.");
        }
        if (jwtProperties.getAudienceApi() == null || jwtProperties.getAudienceApi().isBlank()) {
            throw new IllegalStateException("Production requires jwt.audience-api.");
        }
        if (jwtProperties.getAudienceRealtime() == null || jwtProperties.getAudienceRealtime().isBlank()) {
            throw new IllegalStateException("Production requires jwt.audience-realtime.");
        }
        if (jwtProperties.getAllowedAlgorithms() == null || jwtProperties.getAllowedAlgorithms().isEmpty()) {
            throw new IllegalStateException("Production requires jwt.allowed-algorithms.");
        }
    }

    private void validateCookieSecurity() {
        if (!jwtProperties.getCookie().isSecure()) {
            throw new IllegalStateException("Production requires secure JWT cookies.");
        }
        if (!jwtProperties.getCookie().isHttpOnly()) {
            throw new IllegalStateException("Production requires HttpOnly JWT cookies.");
        }
        if (!csrfProperties.isSecure()) {
            throw new IllegalStateException("Production requires secure CSRF cookies.");
        }
    }

    private void validateCorsOrigins() {
        List<String> origins = corsProperties.getAllowedOrigins();
        boolean hasLocalOrigin = origins.stream()
                .map(String::toLowerCase)
                .anyMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1"));

        if (hasLocalOrigin) {
            throw new IllegalStateException("Production CORS configuration must not allow localhost origins.");
        }
    }

    private void validateMailFrontendUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(mailProperties.getFrontendVerifyUrl());
        urls.add(mailProperties.getFrontendResetPasswordUrl());
        urls.add(mailProperties.getFrontendTwoFactorRecoveryUrl());
        urls.add(mailProperties.getFrontendGameAccountsUrl());
        urls.add(mailProperties.getFrontendSecurityUrl());

        boolean hasBetaDomain = urls.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::toLowerCase)
                .anyMatch(value -> value.contains(BETA_DOMAIN));

        if (hasBetaDomain) {
            throw new IllegalStateException("Production mail frontend URLs must not point to beta.l2terra.online.");
        }
    }
}
