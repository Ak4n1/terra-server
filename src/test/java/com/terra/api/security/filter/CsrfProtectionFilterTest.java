package com.terra.api.security.filter;

import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import com.terra.api.security.config.CsrfProperties;
import com.terra.api.security.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfProtectionFilterTest {

    @Test
    void shouldAllowProtectedRequestWhenCookieAndHeaderMatch() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(csrfProperties(), jwtProperties(), messageResolver(), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.setCookies(
                new jakarta.servlet.http.Cookie("terra_access_token", "access"),
                new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-token")
        );
        request.addHeader("X-CSRF-TOKEN", "csrf-token");

        assertDoesNotThrow(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()));
    }

    @Test
    void shouldRejectProtectedRequestWhenHeaderIsMissing() throws Exception {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(csrfProperties(), jwtProperties(), messageResolver(), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.setCookies(
                new jakarta.servlet.http.Cookie("terra_access_token", "access"),
                new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-token")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("auth.invalid_csrf_token"));
    }

    @Test
    void shouldSkipProtectionForLogin() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(csrfProperties(), jwtProperties(), messageResolver(), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        assertDoesNotThrow(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()));
    }

    private CsrfProperties csrfProperties() {
        CsrfProperties properties = new CsrfProperties();
        properties.setCookieName("XSRF-TOKEN");
        properties.setHeaderName("X-CSRF-TOKEN");
        return properties;
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessCookieName("terra_access_token");
        properties.setRefreshCookieName("terra_refresh_token");
        return properties;
    }

    private MessageResolver messageResolver() {
        return new MessageResolver(new CurrentLanguageResolver());
    }
}
