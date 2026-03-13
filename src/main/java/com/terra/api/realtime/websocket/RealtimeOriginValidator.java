package com.terra.api.realtime.websocket;

import com.terra.api.config.CorsProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RealtimeOriginValidator {

    private final CorsProperties corsProperties;

    public RealtimeOriginValidator(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    public boolean isAllowed(String origin) {
        String normalizedOrigin = normalize(origin);
        if (normalizedOrigin == null) {
            return false;
        }

        Set<String> allowedOrigins = corsProperties.getAllowedOrigins().stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return allowedOrigins.contains(normalizedOrigin);
    }

    public String normalize(String origin) {
        if (origin == null || origin.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(origin.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }

            int port = uri.getPort();
            if (port < 0) {
                port = "https".equals(normalizedScheme) ? 443 : 80;
            }

            return normalizedScheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
        } catch (Exception exception) {
            return null;
        }
    }
}
