package com.terra.api.realtime.infrastructure.websocket;

import java.util.Set;

public record RealtimePrincipal(
        Long accountId,
        String email,
        long tokenVersion,
        Set<String> roles
) {
    public boolean hasRole(String roleName) {
        return roles.contains(roleName);
    }
}
