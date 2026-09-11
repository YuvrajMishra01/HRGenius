package com.hrgenius.common;

/**
 * Unified API envelope for successful responses:
 * { "success": true, "message": "...", "data": { ... } }
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null);
    }
}
