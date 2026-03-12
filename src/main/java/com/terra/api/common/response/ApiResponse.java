package com.terra.api.common.response;

public record ApiResponse<T>(
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> of(String code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    public static ApiResponse<Void> of(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
