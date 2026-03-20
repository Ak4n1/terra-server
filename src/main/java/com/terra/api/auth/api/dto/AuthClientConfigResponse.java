package com.terra.api.auth.api.dto;

public record AuthClientConfigResponse(
        String csrfCookieName,
        String csrfHeaderName
) {
}
