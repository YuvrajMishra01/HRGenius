package com.hrgenius.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication business logic: credential verification and logout
 * (token-version bump). Auth events are logged WITHOUT credentials.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginProtectionService loginProtection;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Phase 24: a currently-locked email is rejected before any credential
        // work — same generic message, no existence information. (The check
        // runs even for emails that never existed; nothing is revealed.)
        if (loginProtection.isLocked(request.email())) {
            log.warn("Login blocked: temporary lockout active [{}]", request.email());
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> {
                    loginProtection.recordFailure(request.email());
                    log.warn("Login failed: unknown email [{}]", request.email());
                    return new BadCredentialsException("Invalid email or password");
                });

        if (!user.isEnabled()) {
            log.warn("Login failed: account disabled [{}]", user.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean lockedNow = loginProtection.recordFailure(request.email());
            log.warn("Login failed: wrong password [{}]{}", user.getEmail(),
                    lockedNow ? " — temporary lockout triggered" : "");
            throw new BadCredentialsException("Invalid email or password");
        }

        loginProtection.recordSuccess(request.email());
        String token = jwtService.generateToken(user);
        log.info("Login succeeded: [{}] role={}", user.getEmail(), user.getRole());
        return AuthResponse.of(user, token, jwtService.getExpirationMinutes());
    }

    /**
     * Stateless logout: bumping TOKEN_VERSION instantly invalidates every
     * token ever issued to this user, without maintaining a blacklist.
     */
    @Transactional
    public void logout(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
            log.info("Logout: invalidated tokens for [{}]", email);
        });
    }
}
