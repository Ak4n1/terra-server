package com.terra.api.config;

import com.terra.api.security.config.CsrfProperties;
import com.terra.api.security.config.JwtProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {

    private static final String DEFAULT_JWT_SECRET = "4d2a9f16b1c4e8870f52c93d7a6e14fb5c20a8d31e74f9b6c2d11a5e9f08bc47";

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final CsrfProperties csrfProperties;
    private final CorsProperties corsProperties;

    public ProductionSecurityValidator(Environment environment,
                                       JwtProperties jwtProperties,
                                       CsrfProperties csrfProperties,
                                       CorsProperties corsProperties) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.csrfProperties = csrfProperties;
        this.corsProperties = corsProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.matchesProfiles("prod")) {
            return;
        }

        validateJwtSecret();
        validateCookieSecurity();
        validateCorsOrigins();
    }

    private void validateJwtSecret() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank() || DEFAULT_JWT_SECRET.equals(secret)) {
            throw new IllegalStateException("Production requires a strong JWT secret configured outside default values.");
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
}
