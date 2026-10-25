package com.hrgenius.auth;

/**
 * Response body for a successful login / GET /auth/me.
 * The JWT is handed to the Angular client here only; it is never persisted
 * server-side and never returned anywhere else.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        Long userId,
        String email,
        String fullName,
        String role
) {

    public static AuthResponse of(User user, String token, long expirationMinutes) {
        return new AuthResponse(token, "Bearer", expirationMinutes,
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
