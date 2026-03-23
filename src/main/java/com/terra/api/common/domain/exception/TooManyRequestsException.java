package com.terra.api.common.domain.exception;

public class TooManyRequestsException extends RuntimeException {
    private final String code;
    private final long retryAfterSeconds;
    private final Object[] messageArgs;

    public TooManyRequestsException(String code, long retryAfterSeconds) {
        super(code);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
        this.messageArgs = new Object[] { retryAfterSeconds };
    }

    public TooManyRequestsException(String code, long retryAfterSeconds, Object... messageArgs) {
        super(code);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
        this.messageArgs = messageArgs == null ? new Object[] { retryAfterSeconds } : messageArgs;
    }

    public String getCode() {
        return code;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
