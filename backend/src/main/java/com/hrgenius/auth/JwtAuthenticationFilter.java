package com.hrgenius.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;

/**
 * Stateless JWT authentication filter. Runs before the username/password
 * machinery on every request:
 *
 *   Authorization: Bearer <token>
 *     → parse + validate signature/expiry
 *     → load user (also checks ENABLED and TOKEN_VERSION so logout
 *       and deactivation take effect immediately)
 *     → populate SecurityContext with ROLE_* authorities
 *
 * No session is ever created (SessionCreationPolicy.STATELESS).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtService.parseToken(header.substring(7));
            if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = jwtService.extractUserId(claims);
                long tokenVersion = jwtService.extractTokenVersion(claims);

                // DB check makes logout (TOKEN_VERSION bump) and ENABLED=0
                // effective immediately; also guarantees the user still exists.
                userRepository.findById(userId).filter(User::isEnabled)
                        .filter(u -> u.getTokenVersion() == tokenVersion)
                        .ifPresent(user -> {
                            List<SimpleGrantedAuthority> authorities =
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                            var principal = new org.springframework.security.core.userdetails.User(
                                    user.getEmail(), "[PROTECTED]", authorities);
                            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            }
        }
        filterChain.doFilter(request, response);
    }
}
