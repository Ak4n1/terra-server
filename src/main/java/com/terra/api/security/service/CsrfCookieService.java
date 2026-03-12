package com.terra.api.security.service;

import com.terra.api.security.config.CsrfProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class CsrfCookieService {

    private final CsrfProperties csrfProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CsrfCookieService(CsrfProperties csrfProperties) {
        this.csrfProperties = csrfProperties;
    }

    public String generateToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public void addCookie(HttpHeaders headers, String token) {
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(token, Duration.ofDays(30)).toString());
    }

    public void clearCookie(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String token, Duration maxAge) {
        return ResponseCookie.from(csrfProperties.getCookieName(), token)
                .httpOnly(csrfProperties.isHttpOnly())
                .secure(csrfProperties.isSecure())
                .sameSite(csrfProperties.getSameSite())
                .path(csrfProperties.getPath())
                .maxAge(maxAge)
                .build();
    }
}
