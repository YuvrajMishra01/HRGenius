package com.hrgenius.audit;

/** Stable API contract for the audit log (Phase 18). */
public final class AuditLogDto {

    private AuditLogDto() {
    }

    /**
     * One audit row. Actor fields are denormalized copies made at write
     * time; createdAt is the DB write timestamp (ISO local date-time).
     */
    public record AuditLogResponse(
            Long id,
            Long actorId,
            String actorEmail,
            String actorName,
            String actorRole,
            String action,
            String entityType,
            Long entityId,
            String entityLabel,
            String details,
            String createdAt) {
    }
}
