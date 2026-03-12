package com.terra.api.security.service;

import com.terra.api.security.config.JwtProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class JwtCookieService {

    private final JwtProperties jwtProperties;

    public JwtCookieService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public void addAccessTokenCookie(HttpHeaders headers, String token) {
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(
                jwtProperties.getAccessCookieName(),
                token,
                Duration.ofMinutes(jwtProperties.getAccessTokenExpirationMinutes())
        ).toString());
    }

    public void addRefreshTokenCookie(HttpHeaders headers, String token) {
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(
                jwtProperties.getRefreshCookieName(),
                token,
                Duration.ofDays(jwtProperties.getRefreshTokenExpirationDays())
        ).toString());
    }

    public void clearAuthenticationCookies(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(jwtProperties.getAccessCookieName(), "", Duration.ZERO).toString());
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(jwtProperties.getRefreshCookieName(), "", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String cookieName, String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(jwtProperties.getCookie().isHttpOnly())
                .secure(jwtProperties.getCookie().isSecure())
                .sameSite(jwtProperties.getCookie().getSameSite())
                .path(jwtProperties.getCookie().getPath())
                .maxAge(maxAge)
                .build();
    }
}
