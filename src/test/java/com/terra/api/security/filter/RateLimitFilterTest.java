package com.terra.api.security.filter;

import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import com.terra.api.security.config.RateLimitProperties;
import com.terra.api.security.config.SecurityNetworkProperties;
import com.terra.api.security.service.ClientIpResolver;
import com.terra.api.security.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    @Test
    void shouldReturn429WhenAuthCriticalPolicyIsExceeded() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getAuthCritical().setEnabled(true);
        properties.getAuthCritical().setCapacity(2);
        properties.getAuthCritical().setRefillTokens(2);
        properties.getAuthCritical().setRefillDurationSeconds(60);

        MessageResolver messageResolver = new MessageResolver(new CurrentLanguageResolver());
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitService(),
                properties,
                messageResolver,
                new ObjectMapper(),
                clientIpResolver(false)
        );

        filter.doFilter(newRequest("/api/auth/login"), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(newRequest("/api/auth/login"), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/login"), blockedResponse, new MockFilterChain());

        assertEquals(429, blockedResponse.getStatus());
        assertTrue(blockedResponse.getContentAsString().contains("\"code\":\"rate_limit.exceeded\""));
        assertTrue(blockedResponse.getContentAsString().contains("retryAfterSeconds"));
    }

    @Test
    void shouldUseEmailInAuthCriticalKeySoDifferentAccountsDoNotShareSameBucket() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getAuthCritical().setEnabled(true);
        properties.getAuthCritical().setCapacity(1);
        properties.getAuthCritical().setRefillTokens(1);
        properties.getAuthCritical().setRefillDurationSeconds(60);

        MessageResolver messageResolver = new MessageResolver(new CurrentLanguageResolver());
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitService(),
                properties,
                messageResolver,
                new ObjectMapper(),
                clientIpResolver(false)
        );

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/login", "same-ip-1@l2terra.online"), firstResponse, new MockFilterChain());

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/login", "same-ip-2@l2terra.online"), secondResponse, new MockFilterChain());

        assertEquals(200, firstResponse.getStatus());
        assertEquals(200, secondResponse.getStatus());
    }

    @Test
    void shouldRateLimitResetPasswordWithAuthCriticalPolicy() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getAuthCritical().setEnabled(true);
        properties.getAuthCritical().setCapacity(1);
        properties.getAuthCritical().setRefillTokens(1);
        properties.getAuthCritical().setRefillDurationSeconds(60);

        MessageResolver messageResolver = new MessageResolver(new CurrentLanguageResolver());
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitService(),
                properties,
                messageResolver,
                new ObjectMapper(),
                clientIpResolver(false)
        );

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/reset-password"), firstResponse, new MockFilterChain());

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/reset-password"), blockedResponse, new MockFilterChain());

        assertEquals(200, firstResponse.getStatus());
        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void shouldApplyDedicatedSessionReadPolicyToCurrentSessionEndpoint() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getAuthSessionRead().setEnabled(true);
        properties.getAuthSessionRead().setCapacity(2);
        properties.getAuthSessionRead().setRefillTokens(2);
        properties.getAuthSessionRead().setRefillDurationSeconds(60);
        properties.getAuthSessionRefresh().setEnabled(true);
        properties.getAuthSessionRefresh().setCapacity(1);
        properties.getAuthSessionRefresh().setRefillTokens(1);
        properties.getAuthSessionRefresh().setRefillDurationSeconds(60);

        MessageResolver messageResolver = new MessageResolver(new CurrentLanguageResolver());
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitService(),
                properties,
                messageResolver,
                new ObjectMapper(),
                clientIpResolver(false)
        );

        filter.doFilter(newRequest("/api/auth/me"), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(newRequest("/api/auth/me"), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/me"), blockedResponse, new MockFilterChain());

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void shouldApplyDedicatedRefreshPolicyToRefreshEndpoint() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getAuthSessionRead().setEnabled(true);
        properties.getAuthSessionRead().setCapacity(10);
        properties.getAuthSessionRead().setRefillTokens(10);
        properties.getAuthSessionRead().setRefillDurationSeconds(60);
        properties.getAuthSessionRefresh().setEnabled(true);
        properties.getAuthSessionRefresh().setCapacity(1);
        properties.getAuthSessionRefresh().setRefillTokens(1);
        properties.getAuthSessionRefresh().setRefillDurationSeconds(60);

        MessageResolver messageResolver = new MessageResolver(new CurrentLanguageResolver());
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitService(),
                properties,
                messageResolver,
                new ObjectMapper(),
                clientIpResolver(false)
        );

        filter.doFilter(newRequest("/api/auth/refresh"), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/refresh"), blockedResponse, new MockFilterChain());

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void shouldIgnoreSpoofedForwardedHeaderWhenTrustIsDisabled() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getAuthCritical().setEnabled(true);
        properties.getAuthCritical().setCapacity(1);
        properties.getAuthCritical().setRefillTokens(1);
        properties.getAuthCritical().setRefillDurationSeconds(60);

        MessageResolver messageResolver = new MessageResolver(new CurrentLanguageResolver());
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitService(),
                properties,
                messageResolver,
                new ObjectMapper(),
                clientIpResolver(false)
        );

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/login", "player@l2terra.online", "198.51.100.10"), firstResponse, new MockFilterChain());

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/login", "player@l2terra.online", "203.0.113.77"), blockedResponse, new MockFilterChain());

        assertEquals(200, firstResponse.getStatus());
        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void shouldUseForwardedHeaderWhenTrustIsEnabled() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getAuthCritical().setEnabled(true);
        properties.getAuthCritical().setCapacity(1);
        properties.getAuthCritical().setRefillTokens(1);
        properties.getAuthCritical().setRefillDurationSeconds(60);

        MessageResolver messageResolver = new MessageResolver(new CurrentLanguageResolver());
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitService(),
                properties,
                messageResolver,
                new ObjectMapper(),
                clientIpResolver(true)
        );

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/login", "player@l2terra.online", "198.51.100.10"), firstResponse, new MockFilterChain());

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(newRequest("/api/auth/login", "player@l2terra.online", "203.0.113.77"), secondResponse, new MockFilterChain());

        assertEquals(200, firstResponse.getStatus());
        assertEquals(200, secondResponse.getStatus());
    }

    private MockHttpServletRequest newRequest(String uri) {
        return newRequest(uri, "player@l2terra.online");
    }

    private MockHttpServletRequest newRequest(String uri, String email) {
        return newRequest(uri, email, null);
    }

    private MockHttpServletRequest newRequest(String uri, String email, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("127.0.0.1");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        request.setContentType("application/json");
        request.setContent("""
                {
                  "email": "%s",
                  "password": "Password1!"
                }
                """.formatted(email).getBytes());
        return request;
    }

    private ClientIpResolver clientIpResolver(boolean trustForwardedHeaders) {
        SecurityNetworkProperties properties = new SecurityNetworkProperties();
        properties.setTrustForwardedHeaders(trustForwardedHeaders);
        return new ClientIpResolver(properties);
    }
}
