package com.hrgenius.config;

import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrgenius.auth.JwtAuthenticationFilter;
import com.hrgenius.common.ApiError;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless JWT security (Phase 1).
 *
 * - Every request passes through JwtAuthenticationFilter first.
 * - Public paths (login, health, docs) come from application.yml so they
 *   can be adjusted per environment without recompiling.
 * - Per-role access control uses @PreAuthorize on controller methods.
 * - Unauthenticated requests get a 401 with the standard ApiError JSON
 *   envelope (Spring's default would be a bare 403); authenticated but
 *   insufficiently privileged requests get a 403 with the same shape.
 * - CORS is configured at the security-filter level so preflight OPTIONS
 *   requests from the Angular dev server are answered before authorization.
 * - CSRF is disabled: there are no cookies and no server sessions — the
 *   bearer token is sent explicitly by the SPA, so CSRF does not apply.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfig corsConfig;
    private final String[] publicPaths;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CorsConfig corsConfig,
                          @Value("${hrgenius.security.public-paths}") String publicPathsCsv) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfig = corsConfig;
        this.publicPaths = Arrays.stream(publicPathsCsv.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(publicPaths).permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(unauthorizedEntryPoint())
                .accessDeniedHandler(forbiddenHandler()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 401 with the standard error envelope for missing/invalid/expired tokens. */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required", request);
    }

    /** 403 with the standard error envelope for insufficient role. */
    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, accessDeniedException) ->
                writeError(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message,
                            HttpServletRequest request) {
        try {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError body = ApiError.of(status, message, request.getRequestURI());
            JSON.writeValue(response.getOutputStream(), body);
        } catch (Exception io) {
            // Response may already be committed; nothing further we can do.
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        for (String origin : corsConfig.getAllowedOrigins().split(",")) {
            config.addAllowedOrigin(origin.trim());
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
