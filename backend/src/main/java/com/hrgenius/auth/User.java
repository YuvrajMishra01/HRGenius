package com.hrgenius.auth;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Application login account. Maps to the USERS table created by Flyway
 * migration V1. Passwords are only ever stored as BCrypt hashes
 * (PASSWORD_HASH) and are never returned by any API.
 *
 * TOKEN_VERSION (added by migration V3) implements stateless logout:
 * every JWT carries the version it was issued with. POST /auth/logout
 * increments the column, which instantly invalidates all previously
 * issued tokens for this user without keeping a token blacklist.
 */
@Entity
@Table(name = "USERS")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMAIL", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "FULL_NAME", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    private Role role;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled = true;

    /** Incremented on logout; JWTs with a lower version are rejected. */
    @Column(name = "TOKEN_VERSION", nullable = false)
    private long tokenVersion = 0;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
