package com.terra.api.security.service;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountSession;
import com.terra.api.auth.repository.AccountSessionRepository;
import com.terra.api.security.config.SecurityNetworkProperties;
import com.terra.api.security.jwt.JwtAuthenticationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSessionServiceTest {

    @Mock
    private AccountSessionRepository accountSessionRepository;

    @Test
    void shouldCreateSessionWithRequestMetadata() {
        AccountMaster accountMaster = accountMaster(10L);
        MockHttpServletRequest request = request();
        AccountSessionService accountSessionService = new AccountSessionService(accountSessionRepository, clientIpResolver(false));

        accountSessionService.createSession(accountMaster, "refresh-token", Instant.now().plusSeconds(60), request);

        ArgumentCaptor<AccountSession> captor = ArgumentCaptor.forClass(AccountSession.class);
        verify(accountSessionRepository).save(captor.capture());
        AccountSession savedSession = captor.getValue();
        assertEquals(accountMaster, savedSession.getAccount());
        assertEquals("10.0.0.1", savedSession.getIpAddress());
        assertEquals("Mozilla Test", savedSession.getUserAgent());
        assertNotNull(savedSession.getRefreshTokenHash());
    }

    @Test
    void shouldRotateExistingSession() {
        AccountMaster accountMaster = accountMaster(10L);
        AccountSession currentSession = activeSession(accountMaster);
        AccountSessionService accountSessionService = new AccountSessionService(accountSessionRepository, clientIpResolver(false));
        when(accountSessionRepository.findByRefreshTokenHash(any())).thenReturn(Optional.of(currentSession));

        accountSessionService.rotateSession(
                accountMaster,
                "old-refresh-token",
                "new-refresh-token",
                Instant.now().plusSeconds(120),
                request()
        );

        verify(accountSessionRepository, times(1)).findByRefreshTokenHash(any());
        verify(accountSessionRepository, times(1)).save(any(AccountSession.class));
        assertNotNull(currentSession.getRevokedAt());
    }

    @Test
    void shouldRejectInactiveSession() {
        AccountMaster accountMaster = accountMaster(10L);
        AccountSession session = activeSession(accountMaster);
        AccountSessionService accountSessionService = new AccountSessionService(accountSessionRepository, clientIpResolver(false));
        session.setRevokedAt(Instant.now());
        when(accountSessionRepository.findByRefreshTokenHash(any())).thenReturn(Optional.of(session));

        assertThrows(JwtAuthenticationException.class, () -> accountSessionService.getActiveSession("refresh-token"));
    }

    @Test
    void shouldRevokeAllActiveSessionsForAccount() {
        AccountMaster accountMaster = accountMaster(10L);
        AccountSession activeOne = activeSession(accountMaster);
        AccountSession activeTwo = activeSession(accountMaster);
        AccountSessionService accountSessionService = new AccountSessionService(accountSessionRepository, clientIpResolver(false));
        when(accountSessionRepository.findByAccount_Id(10L)).thenReturn(java.util.List.of(activeOne, activeTwo));

        accountSessionService.revokeAllSessions(accountMaster);

        assertNotNull(activeOne.getRevokedAt());
        assertNotNull(activeTwo.getRevokedAt());
        verify(accountSessionRepository).findByAccount_Id(10L);
    }

    @Test
    void shouldIgnoreSpoofedForwardedHeaderWhenTrustIsDisabled() {
        AccountMaster accountMaster = accountMaster(10L);
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "198.51.100.10");
        AccountSessionService accountSessionService = new AccountSessionService(accountSessionRepository, clientIpResolver(false));

        accountSessionService.createSession(accountMaster, "refresh-token", Instant.now().plusSeconds(60), request);

        ArgumentCaptor<AccountSession> captor = ArgumentCaptor.forClass(AccountSession.class);
        verify(accountSessionRepository).save(captor.capture());
        assertEquals("10.0.0.1", captor.getValue().getIpAddress());
    }

    @Test
    void shouldUseForwardedHeaderWhenTrustIsEnabled() {
        AccountMaster accountMaster = accountMaster(10L);
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.1");
        AccountSessionService accountSessionService = new AccountSessionService(accountSessionRepository, clientIpResolver(true));

        accountSessionService.createSession(accountMaster, "refresh-token", Instant.now().plusSeconds(60), request);

        ArgumentCaptor<AccountSession> captor = ArgumentCaptor.forClass(AccountSession.class);
        verify(accountSessionRepository).save(captor.capture());
        assertEquals("198.51.100.10", captor.getValue().getIpAddress());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("User-Agent", "Mozilla Test");
        return request;
    }

    private ClientIpResolver clientIpResolver(boolean trustForwardedHeaders) {
        SecurityNetworkProperties properties = new SecurityNetworkProperties();
        properties.setTrustForwardedHeaders(trustForwardedHeaders);
        return new ClientIpResolver(properties);
    }

    private AccountMaster accountMaster(Long id) {
        AccountMaster accountMaster = new AccountMaster();
        try {
            var field = AccountMaster.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(accountMaster, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return accountMaster;
    }

    private AccountSession activeSession(AccountMaster accountMaster) {
        AccountSession session = new AccountSession();
        session.setAccount(accountMaster);
        session.setRefreshTokenHash("hash");
        session.setExpiresAt(Instant.now().plusSeconds(60));
        return session;
    }
}
