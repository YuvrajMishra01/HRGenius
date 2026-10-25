package com.hrgenius.common;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Error body returned by GlobalExceptionHandler for every failure:
 * { "timestamp", "status", "message", "path", "errors": { field: message } }
 */
public record ApiError(
        String timestamp,
        int status,
        String message,
        String path,
        Map<String, String> errors
) {
    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(Instant.now().toString(), status.value(), message, path, Map.of());
    }

    public static ApiError of(HttpStatus status, String message, String path, Map<String, String> errors) {
        return new ApiError(Instant.now().toString(), status.value(), message, path, errors);
    }
}
