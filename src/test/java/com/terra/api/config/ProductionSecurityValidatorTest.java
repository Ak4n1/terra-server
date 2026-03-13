package com.terra.api.config;

import com.terra.api.security.config.CsrfProperties;
import com.terra.api.security.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityValidatorTest {

    @Test
    void shouldRejectDefaultJwtSecretInProduction() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        JwtProperties jwtProperties = jwtProperties(
                "4d2a9f16b1c4e8870f52c93d7a6e14fb5c20a8d31e74f9b6c2d11a5e9f08bc47",
                true,
                true
        );
        CsrfProperties csrfProperties = csrfProperties(true);
        CorsProperties corsProperties = corsProperties("https://l2terra.online");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                environment,
                jwtProperties,
                csrfProperties,
                corsProperties
        );

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectLocalhostCorsOriginInProduction() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        JwtProperties jwtProperties = jwtProperties("prodSecretKey123456789012345678901234567890", true, true);
        CsrfProperties csrfProperties = csrfProperties(true);
        CorsProperties corsProperties = corsProperties("http://localhost:4200");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                environment,
                jwtProperties,
                csrfProperties,
                corsProperties
        );

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldAllowSafeProductionConfiguration() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        JwtProperties jwtProperties = jwtProperties("prodSecretKey123456789012345678901234567890", true, true);
        CsrfProperties csrfProperties = csrfProperties(true);
        CorsProperties corsProperties = corsProperties("https://l2terra.online");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                environment,
                jwtProperties,
                csrfProperties,
                corsProperties
        );

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    private JwtProperties jwtProperties(String secret, boolean secure, boolean httpOnly) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(secret);
        jwtProperties.getCookie().setSecure(secure);
        jwtProperties.getCookie().setHttpOnly(httpOnly);
        return jwtProperties;
    }

    private CsrfProperties csrfProperties(boolean secure) {
        CsrfProperties csrfProperties = new CsrfProperties();
        csrfProperties.setSecure(secure);
        return csrfProperties;
    }

    private CorsProperties corsProperties(String origin) {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(java.util.List.of(origin));
        return corsProperties;
    }
}
