package com.terra.api.realtime.websocket;

import com.terra.api.realtime.session.RealtimeSession;
import com.terra.api.realtime.session.RealtimeSessionService;
import com.terra.api.security.jwt.JwtAuthenticationException;
import com.terra.api.security.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final RealtimeOriginValidator originValidator;
    private final RealtimeAuthenticationService realtimeAuthenticationService;
    private final RealtimeHandshakeRateLimiter handshakeRateLimiter;
    private final RealtimeSessionService realtimeSessionService;
    private final ClientIpResolver clientIpResolver;

    public WebSocketHandshakeInterceptor(RealtimeOriginValidator originValidator,
                                         RealtimeAuthenticationService realtimeAuthenticationService,
                                         RealtimeHandshakeRateLimiter handshakeRateLimiter,
                                         RealtimeSessionService realtimeSessionService,
                                         ClientIpResolver clientIpResolver) {
        this.originValidator = originValidator;
        this.realtimeAuthenticationService = realtimeAuthenticationService;
        this.handshakeRateLimiter = handshakeRateLimiter;
        this.realtimeSessionService = realtimeSessionService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
        String originHeader = httpServletRequest.getHeader("Origin");
        if (!originValidator.isAllowed(originHeader)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        String clientIp = clientIpResolver.resolve(httpServletRequest);
        if (!handshakeRateLimiter.allowIp(clientIp)) {
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }

        RealtimeAuthenticationService.AuthenticatedRealtimeAccount authenticatedAccount;
        try {
            authenticatedAccount = realtimeAuthenticationService.authenticate(httpServletRequest);
        } catch (JwtAuthenticationException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        RealtimePrincipal principal = authenticatedAccount.principal();
        if (!handshakeRateLimiter.allowAccount(principal.accountId())) {
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }

        String normalizedOrigin = originValidator.normalize(originHeader);
        RealtimeSession realtimeSession = realtimeSessionService.openSession(
                authenticatedAccount.accountMaster(),
                authenticatedAccount.accountSession(),
                normalizedOrigin,
                clientIp,
                httpServletRequest.getHeader("User-Agent")
        );

        attributes.put(RealtimeHandshakeAttributes.REALTIME_SESSION_ID, realtimeSession.getRealtimeSessionId());
        attributes.put(RealtimeHandshakeAttributes.PRINCIPAL, principal);
        attributes.put(RealtimeHandshakeAttributes.ACCOUNT_ID, principal.accountId());
        attributes.put(RealtimeHandshakeAttributes.ACCOUNT_SESSION_ID, authenticatedAccount.accountSession().getId());
        attributes.put(RealtimeHandshakeAttributes.ACCOUNT_EMAIL, principal.email());
        attributes.put(RealtimeHandshakeAttributes.ORIGIN, normalizedOrigin);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
