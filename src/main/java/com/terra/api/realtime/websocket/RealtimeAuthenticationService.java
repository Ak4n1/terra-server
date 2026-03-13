package com.terra.api.realtime.websocket;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.AccountSession;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.security.config.JwtProperties;
import com.terra.api.security.jwt.JwtAuthenticationException;
import com.terra.api.security.jwt.JwtService;
import com.terra.api.security.jwt.JwtTokenType;
import com.terra.api.security.service.AccountSessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RealtimeAuthenticationService {

    public record AuthenticatedRealtimeAccount(
            AccountMaster accountMaster,
            AccountSession accountSession,
            RealtimePrincipal principal
    ) {
    }

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AccountMasterRepository accountMasterRepository;
    private final AccountSessionService accountSessionService;

    public RealtimeAuthenticationService(JwtService jwtService,
                                         JwtProperties jwtProperties,
                                         AccountMasterRepository accountMasterRepository,
                                         AccountSessionService accountSessionService) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.accountMasterRepository = accountMasterRepository;
        this.accountSessionService = accountSessionService;
    }

    public AuthenticatedRealtimeAccount authenticate(HttpServletRequest request) {
        String accessToken = extractAccessToken(request);
        String refreshToken = extractRefreshToken(request);
        if (accessToken == null || accessToken.isBlank()) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new JwtAuthenticationException("auth.invalid_refresh_token");
        }

        Long accountId = jwtService.extractAccountId(accessToken, JwtTokenType.ACCESS);
        AccountMaster accountMaster = accountMasterRepository.findById(accountId)
                .orElseThrow(() -> new JwtAuthenticationException("auth.invalid_token"));
        AccountSession accountSession = accountSessionService.getActiveSession(refreshToken);
        if (!accountSession.getAccount().getId().equals(accountId)) {
            throw new JwtAuthenticationException("auth.invalid_refresh_token");
        }

        long tokenVersion = jwtService.extractTokenVersion(accessToken, JwtTokenType.ACCESS);
        if (tokenVersion != accountMaster.getTokenVersion()) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }

        if (!accountMaster.isEnabled() || !accountMaster.isEmailVerified()) {
            throw new JwtAuthenticationException("auth.invalid_token");
        }

        Set<String> roles = accountMaster.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());

        RealtimePrincipal principal = new RealtimePrincipal(
                accountMaster.getId(),
                accountMaster.getEmail(),
                accountMaster.getTokenVersion(),
                roles
        );

        return new AuthenticatedRealtimeAccount(accountMaster, accountSession, principal);
    }

    private String extractAccessToken(HttpServletRequest request) {
        return extractCookieValue(request, jwtProperties.getAccessCookieName());
    }

    private String extractRefreshToken(HttpServletRequest request) {
        return extractCookieValue(request, jwtProperties.getRefreshCookieName());
    }

    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
