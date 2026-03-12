package com.terra.api.auth.dto;

public record AuthClientConfigResponse(
        String csrfCookieName,
        String csrfHeaderName
) {
}
