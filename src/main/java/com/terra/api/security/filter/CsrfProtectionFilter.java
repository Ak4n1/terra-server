package com.terra.api.security.filter;

import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.response.ApiResponse;
import com.terra.api.security.config.CsrfProperties;
import com.terra.api.security.config.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> EXCLUDED_PATHS = Set.of("/api/auth/login", "/api/auth/register");

    private final CsrfProperties csrfProperties;
    private final JwtProperties jwtProperties;
    private final MessageResolver messageResolver;
    private final ObjectMapper objectMapper;

    public CsrfProtectionFilter(CsrfProperties csrfProperties,
                                JwtProperties jwtProperties,
                                MessageResolver messageResolver,
                                ObjectMapper objectMapper) {
        this.csrfProperties = csrfProperties;
        this.jwtProperties = jwtProperties;
        this.messageResolver = messageResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (requiresProtection(request)) {
            String cookieToken = extractCookieValue(request, csrfProperties.getCookieName());
            String headerToken = request.getHeader(csrfProperties.getHeaderName());

            if (cookieToken == null || cookieToken.isBlank() || headerToken == null || headerToken.isBlank() || !cookieToken.equals(headerToken)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(
                        response.getOutputStream(),
                        ApiResponse.of("auth.invalid_csrf_token", messageResolver.get("auth.invalid_csrf_token"))
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresProtection(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }

        if (EXCLUDED_PATHS.contains(request.getRequestURI())) {
            return false;
        }

        return hasCookie(request, jwtProperties.getAccessCookieName()) || hasCookie(request, jwtProperties.getRefreshCookieName());
    }

    private boolean hasCookie(HttpServletRequest request, String cookieName) {
        return extractCookieValue(request, cookieName) != null;
    }

    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
