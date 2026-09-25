package com.hrgenius.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * One immutable row per important state-changing HR action (Phase 18).
 *
 * Actor data is deliberately denormalized (email/name/role copied at write
 * time): the audit trail must stay readable even when a user is later
 * renamed, disabled, or removed, so it is never joined back to USERS for
 * display. Rows are only ever inserted — no update or delete API exists.
 *
 * CREATED_AT is DB-defaulted (Flyway V5), mirroring NOTIFICATIONS.
 */
@Entity
@Table(name = "AUDIT_LOG")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ACTOR_ID")
    private Long actorId;

    @Column(name = "ACTOR_EMAIL", length = 150)
    private String actorEmail;

    @Column(name = "ACTOR_NAME", length = 150)
    private String actorName;

    @Column(name = "ACTOR_ROLE", length = 20)
    private String actorRole;

    /** Machine-readable action, e.g. LEAVE_REQUEST_APPROVED. */
    @Column(name = "ACTION", nullable = false, length = 60)
    private String action;

    /** Coarse entity family, e.g. LEAVE_REQUEST — the filterable facet. */
    @Column(name = "ENTITY_TYPE", nullable = false, length = 40)
    private String entityType;

    @Column(name = "ENTITY_ID")
    private Long entityId;

    /** Human-readable target, e.g. "Anita Desai (EMP002)". */
    @Column(name = "ENTITY_LABEL", length = 200)
    private String entityLabel;

    /** Structured detail line, e.g. "PENDING → APPROVED by hr@hrgenius.local". */
    @Column(name = "DETAILS", length = 1000)
    private String details;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
