package com.hrgenius.auth;

import javax.crypto.SecretKey;

import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and parses HS256-signed JWTs.
 *
 * SECURITY: the signing secret must be at least 32 bytes for HS256 and is
 * injected via the JWT_SECRET environment variable outside local dev
 * (application.yml holds only a documented local default).
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_VER = "ver";

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMinutes = expirationMinutes;
    }

    /** Creates a signed token carrying subject (email), uid, role and token version. */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMinutes * 60_000);
        return Jwts.builder()
                .subject(user.getEmail())
                .claims(Map.of(
                        CLAIM_ROLE, user.getRole().name(),
                        "uid", user.getId(),
                        CLAIM_VER, user.getTokenVersion()))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Test support: issue a token with explicit claims and an expiry offset
     * in minutes from now (negative values produce an already-expired token).
     */
    public String generateTokenWithOverride(String email, String role, Long uid, long ver, long offsetMinutes) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + offsetMinutes * 60_000);
        return Jwts.builder()
                .subject(email)
                .claims(Map.of(
                        CLAIM_ROLE, role,
                        "uid", uid,
                        CLAIM_VER, ver))
                .issuedAt(new Date(now.getTime() - 61 * 60_000))
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates signature + expiry. Returns the claims or null if
     * the token is malformed, tampered with or expired.
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    public String extractRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public Long extractUserId(Claims claims) {
        Number uid = claims.get("uid", Number.class);
        return uid == null ? null : uid.longValue();
    }

    public long extractTokenVersion(Claims claims) {
        Number ver = claims.get(CLAIM_VER, Number.class);
        return ver == null ? 0L : ver.longValue();
    }

    /** Expiry in milliseconds — exposed so tests can create expired tokens. */
    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
