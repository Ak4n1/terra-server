package com.terra.api.common.exception;

public class ResourceConflictException extends RuntimeException {
    private final String code;

    public ResourceConflictException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
