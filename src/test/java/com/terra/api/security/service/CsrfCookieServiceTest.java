package com.terra.api.security.service;

import com.terra.api.security.config.CsrfProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfCookieServiceTest {

    @Test
    void shouldGenerateTokenAndSetCookie() {
        CsrfCookieService service = new CsrfCookieService(csrfProperties());
        String token = service.generateToken();
        HttpHeaders headers = new HttpHeaders();

        service.addCookie(headers, token);

        assertNotNull(token);
        assertFalse(token.isBlank());
        String setCookie = headers.getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("XSRF-TOKEN=" + token));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }

    @Test
    void shouldClearCookie() {
        CsrfCookieService service = new CsrfCookieService(csrfProperties());
        HttpHeaders headers = new HttpHeaders();

        service.clearCookie(headers);

        String setCookie = headers.getFirst(HttpHeaders.SET_COOKIE);
        assertEquals(true, setCookie.contains("Max-Age=0"));
    }

    private CsrfProperties csrfProperties() {
        CsrfProperties properties = new CsrfProperties();
        properties.setCookieName("XSRF-TOKEN");
        properties.setHeaderName("X-CSRF-TOKEN");
        properties.setPath("/");
        properties.setSameSite("Lax");
        properties.setSecure(false);
        properties.setHttpOnly(false);
        return properties;
    }
}
