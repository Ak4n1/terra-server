package com.terra.api.common.exception;

public class BadRequestException extends RuntimeException {
    private final String code;

    public BadRequestException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
