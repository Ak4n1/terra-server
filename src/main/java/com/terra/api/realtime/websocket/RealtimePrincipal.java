package com.terra.api.realtime.websocket;

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
