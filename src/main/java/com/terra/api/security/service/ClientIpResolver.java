package com.terra.api.security.service;

import com.terra.api.security.config.SecurityNetworkProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private final SecurityNetworkProperties securityNetworkProperties;

    public ClientIpResolver(SecurityNetworkProperties securityNetworkProperties) {
        this.securityNetworkProperties = securityNetworkProperties;
    }

    public String resolve(HttpServletRequest request) {
        if (!securityNetworkProperties.isTrustForwardedHeaders()) {
            return request.getRemoteAddr();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }

        String clientIp = forwardedFor.split(",")[0].trim();
        return clientIp.isBlank() ? request.getRemoteAddr() : clientIp;
    }
}
