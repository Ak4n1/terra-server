package com.terra.api.security.service;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountSession;
import com.terra.api.auth.repository.AccountSessionRepository;
import com.terra.api.security.jwt.JwtAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Service
public class AccountSessionService {

    private final AccountSessionRepository accountSessionRepository;
    private final ClientIpResolver clientIpResolver;

    public AccountSessionService(AccountSessionRepository accountSessionRepository,
                                 ClientIpResolver clientIpResolver) {
        this.accountSessionRepository = accountSessionRepository;
        this.clientIpResolver = clientIpResolver;
    }

    @Transactional
    public void createSession(AccountMaster accountMaster,
                              String refreshToken,
                              Instant expiresAt,
                              HttpServletRequest request) {
        AccountSession session = new AccountSession();
        session.setAccount(accountMaster);
        session.setRefreshTokenHash(hashToken(refreshToken));
        session.setExpiresAt(expiresAt);
        session.setIpAddress(resolveIpAddress(request));
        session.setUserAgent(resolveUserAgent(request));
        accountSessionRepository.save(session);
    }

    @Transactional
    public void rotateSession(AccountMaster accountMaster,
                              String currentRefreshToken,
                              String newRefreshToken,
                              Instant newExpiresAt,
                              HttpServletRequest request) {
        AccountSession currentSession = getActiveSessionForUpdate(currentRefreshToken);
        if (!currentSession.getAccount().getId().equals(accountMaster.getId())) {
            throw new JwtAuthenticationException("auth.invalid_refresh_token");
        }

        currentSession.setRevokedAt(Instant.now());

        AccountSession replacementSession = new AccountSession();
        replacementSession.setAccount(accountMaster);
        replacementSession.setRefreshTokenHash(hashToken(newRefreshToken));
        replacementSession.setExpiresAt(newExpiresAt);
        replacementSession.setIpAddress(resolveIpAddress(request));
        replacementSession.setUserAgent(resolveUserAgent(request));
        accountSessionRepository.save(replacementSession);
    }

    @Transactional
    public void revokeSession(String refreshToken) {
        accountSessionRepository.findByRefreshTokenHash(hashToken(refreshToken))
                .ifPresent(session -> {
                    if (session.getRevokedAt() == null) {
                        session.setRevokedAt(Instant.now());
                    }
                });
    }

    @Transactional
    public void revokeAllSessions(AccountMaster accountMaster) {
        Instant revokedAt = Instant.now();
        accountSessionRepository.findByAccount_Id(accountMaster.getId())
                .forEach(session -> {
                    if (session.getRevokedAt() == null) {
                        session.setRevokedAt(revokedAt);
                    }
                });
    }

    @Transactional(readOnly = true)
    public AccountSession getActiveSession(String refreshToken) {
        AccountSession session = accountSessionRepository.findByRefreshTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new JwtAuthenticationException("auth.invalid_refresh_token"));

        validateActiveSession(session);
        return session;
    }

    private AccountSession getActiveSessionForUpdate(String refreshToken) {
        AccountSession session = accountSessionRepository.findWithLockByRefreshTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new JwtAuthenticationException("auth.invalid_refresh_token"));

        validateActiveSession(session);
        return session;
    }

    private void validateActiveSession(AccountSession session) {
        if (!session.isActive()) {
            throw new JwtAuthenticationException("auth.invalid_refresh_token");
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String resolveIpAddress(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }
}
