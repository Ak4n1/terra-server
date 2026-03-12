package com.terra.api.security.filter;

import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.security.config.RateLimitProperties;
import com.terra.api.security.service.ClientIpResolver;
import com.terra.api.security.service.RateLimitResult;
import com.terra.api.security.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final MessageResolver messageResolver;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(RateLimitService rateLimitService,
                           RateLimitProperties rateLimitProperties,
                           MessageResolver messageResolver,
                           ObjectMapper objectMapper,
                           ClientIpResolver clientIpResolver) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.messageResolver = messageResolver;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest requestToUse = request;
        if (requiresBodyInspection(request)) {
            requestToUse = new CachedBodyHttpServletRequest(request);
        }

        RateLimitProperties.Policy policy = resolvePolicy(requestToUse);
        if (policy != null) {
            String key = buildKey(requestToUse);
            RateLimitResult result = rateLimitService.tryConsume(key, policy);
            if (!result.requestAllowed()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
                objectMapper.writeValue(
                        response.getOutputStream(),
                        new RateLimitErrorResponse(
                                "rate_limit.exceeded",
                                messageResolver.get("security.rate_limit_exceeded"),
                                result.retryAfterSeconds()
                        )
                );
                return;
            }
        }

        filterChain.doFilter(requestToUse, response);
    }

    private RateLimitProperties.Policy resolvePolicy(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/resend-verification".equals(path)
                || "/api/auth/forgot-password".equals(path)
                || "/api/auth/verify-email".equals(path)
                || "/api/auth/reset-password".equals(path)) {
            return rateLimitProperties.getAuthCritical();
        }
        if ("/api/auth/me".equals(path)) {
            return rateLimitProperties.getAuthSessionRead();
        }
        if ("/api/auth/refresh".equals(path)) {
            return rateLimitProperties.getAuthSessionRefresh();
        }
        if ("/api/auth/logout".equals(path)
                || "/api/auth/logout-all".equals(path)) {
            return rateLimitProperties.getAuthSession();
        }
        return null;
    }

    private String buildKey(HttpServletRequest request) {
        String ipAddress = clientIpResolver.resolve(request);

        if ("/api/auth/login".equals(request.getRequestURI())
                || "/api/auth/register".equals(request.getRequestURI())
                || "/api/auth/resend-verification".equals(request.getRequestURI())
                || "/api/auth/forgot-password".equals(request.getRequestURI())) {
            return ipAddress + ":" + request.getRequestURI() + ":" + extractEmailIdentity(request);
        }

        return ipAddress + ":" + request.getRequestURI();
    }

    private boolean requiresBodyInspection(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!"/api/auth/login".equals(path)
                && !"/api/auth/register".equals(path)
                && !"/api/auth/resend-verification".equals(path)
                && !"/api/auth/forgot-password".equals(path)) {
            return false;
        }

        return request.getContentType() != null
                && request.getContentType().toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private String extractEmailIdentity(HttpServletRequest request) {
        try {
            Map<?, ?> body = objectMapper.readValue(request.getInputStream(), Map.class);
            Object rawEmail = body.get("email");
            if (rawEmail == null) {
                return "anonymous";
            }

            String normalizedEmail = rawEmail.toString().trim().toLowerCase(Locale.ROOT);
            return normalizedEmail.isBlank() ? "anonymous" : normalizedEmail;
        } catch (IOException exception) {
            return "anonymous";
        }
    }

    private record RateLimitErrorResponse(
            String code,
            String message,
            long retryAfterSeconds
    ) {
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
