package com.terra.api.common.exception;

public class TooManyRequestsException extends RuntimeException {
    private final String code;
    private final long retryAfterSeconds;

    public TooManyRequestsException(String code, long retryAfterSeconds) {
        super(code);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
