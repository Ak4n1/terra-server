package com.terra.api.security.application;

import com.terra.api.security.infrastructure.config.SecurityNetworkProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

@Component
public class ClientIpResolver {

    private static final Pattern IP_LITERAL_PATTERN = Pattern.compile("^[0-9a-fA-F:.%]+$");
    private final SecurityNetworkProperties securityNetworkProperties;

    public ClientIpResolver(SecurityNetworkProperties securityNetworkProperties) {
        this.securityNetworkProperties = securityNetworkProperties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = firstNonBlank(request.getRemoteAddr());
        if (!securityNetworkProperties.isTrustForwardedHeaders()) {
            return remoteAddr;
        }
        if (!isTrustedProxyAddress(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }

        for (String value : forwardedFor.split(",")) {
            String candidate = normalizeForwardedValue(value);
            if (candidate == null) {
                continue;
            }
            InetAddress parsedAddress = parseIpLiteral(candidate);
            if (parsedAddress != null) {
                return parsedAddress.getHostAddress();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxyAddress(String value) {
        InetAddress parsedAddress = parseIpLiteral(value);
        if (parsedAddress == null) {
            return false;
        }
        return parsedAddress.isLoopbackAddress()
                || parsedAddress.isSiteLocalAddress()
                || parsedAddress.isLinkLocalAddress()
                || isUniqueLocalIpv6(parsedAddress);
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] rawAddress = address.getAddress();
        return rawAddress.length == 16 && (rawAddress[0] & 0xFE) == 0xFC;
    }

    private InetAddress parseIpLiteral(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!IP_LITERAL_PATTERN.matcher(value).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private String normalizeForwardedValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if ("unknown".equalsIgnoreCase(normalized)) {
            return null;
        }
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.startsWith("[") && normalized.contains("]")) {
            int closingIndex = normalized.indexOf(']');
            return normalized.substring(1, closingIndex);
        }
        int colonCount = 0;
        for (int index = 0; index < normalized.length(); index++) {
            if (normalized.charAt(index) == ':') {
                colonCount++;
            }
        }
        if (colonCount == 1 && normalized.contains(".")) {
            int separatorIndex = normalized.lastIndexOf(':');
            return normalized.substring(0, separatorIndex).trim();
        }
        return normalized;
    }

    private String firstNonBlank(String value) {
        return (value == null || value.isBlank()) ? "0.0.0.0" : value.trim();
    }
}
