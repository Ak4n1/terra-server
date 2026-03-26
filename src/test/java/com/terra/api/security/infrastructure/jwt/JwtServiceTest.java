package com.terra.api.security.infrastructure.jwt;

import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.security.domain.JwtAuthenticationException;
import com.terra.api.security.domain.JwtTokenType;
import com.terra.api.security.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JwtServiceTest {

    @Test
    void shouldGenerateAccessTokenUsingPublicIdAsSubject() {
        JwtService jwtService = new JwtService(jwtProperties(), mock(AccountMasterRepository.class));
        AccountMaster accountMaster = accountMaster(25L, "admin@l2terra.online");
        accountMaster.setPublicId("acc-public-25");

        String accessToken = jwtService.generateAccessToken(accountMaster);

        assertEquals("acc-public-25", jwtService.extractAccountPublicId(accessToken, JwtTokenType.ACCESS));
        assertEquals(0L, jwtService.extractTokenVersion(accessToken, JwtTokenType.ACCESS));
        Instant expiration = jwtService.extractExpiration(accessToken, JwtTokenType.ACCESS);
        assertTrue(expiration.isAfter(Instant.now()));
    }

    @Test
    void shouldRejectTokenWhenTypeDoesNotMatch() {
        JwtService jwtService = new JwtService(jwtProperties(), mock(AccountMasterRepository.class));
        AccountMaster accountMaster = accountMaster(25L, "admin@l2terra.online");
        accountMaster.setPublicId("acc-public-25");

        String refreshToken = jwtService.generateRefreshToken(accountMaster);

        assertThrows(JwtAuthenticationException.class, () -> jwtService.extractAccountPublicId(refreshToken, JwtTokenType.ACCESS));
    }

    @Test
    void shouldNotEmbedEmailInJwtPayload() {
        JwtService jwtService = new JwtService(jwtProperties(), mock(AccountMasterRepository.class));
        AccountMaster accountMaster = accountMaster(25L, "admin@l2terra.online");
        accountMaster.setPublicId("acc-public-25");

        String accessToken = jwtService.generateAccessToken(accountMaster);

        assertFalse(accessToken.contains("admin@l2terra.online"));
    }

    @Test
    void shouldEmbedCurrentTokenVersionInJwtPayload() {
        JwtService jwtService = new JwtService(jwtProperties(), mock(AccountMasterRepository.class));
        AccountMaster accountMaster = accountMaster(25L, "admin@l2terra.online");
        accountMaster.setPublicId("acc-public-25");
        accountMaster.setTokenVersion(4L);

        String refreshToken = jwtService.generateRefreshToken(accountMaster);

        assertEquals(4L, jwtService.extractTokenVersion(refreshToken, JwtTokenType.REFRESH));
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("superSecretKeyForTests12345678901234567890");
        properties.setAccessTokenExpirationMinutes(15);
        properties.setRefreshTokenExpirationDays(30);
        properties.setAccessCookieName("terra_access_token");
        properties.setRefreshCookieName("terra_refresh_token");
        properties.setIssuer("test-auth-server");
        properties.setAudienceApi("test-api");
        properties.setAudienceRealtime("test-realtime");
        properties.setAllowedAlgorithms(java.util.List.of("HS256"));
        properties.getCookie().setPath("/");
        properties.getCookie().setSameSite("Lax");
        properties.getCookie().setHttpOnly(true);
        properties.getCookie().setSecure(false);
        return properties;
    }

    private AccountMaster accountMaster(Long accountId, String email) {
        AccountMaster accountMaster = new AccountMaster();
        accountMaster.setEmail(email);
        try {
            var idField = AccountMaster.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(accountMaster, accountId);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return accountMaster;
    }
}
