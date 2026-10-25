package com.hrgenius.auth;

import com.hrgenius.common.ApiResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints:
 *   POST /api/v1/auth/login   — public, returns JWT + user info
 *   GET  /api/v1/auth/me      — authenticated, returns current user
 *   POST /api/v1/auth/logout  — authenticated, invalidates the caller's tokens
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.of("Login successful", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Not authenticated"));
        return ResponseEntity.ok(ApiResponse.of(new AuthResponse(
                null, null, 0, user.getId(), user.getEmail(), user.getFullName(), user.getRole().name())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        authService.logout(auth.getName());
        return ResponseEntity.ok(ApiResponse.message("Logged out"));
    }
}
