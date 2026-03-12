package com.terra.api.security.jwt;

public class JwtAuthenticationException extends RuntimeException {

    private final String code;

    public JwtAuthenticationException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
