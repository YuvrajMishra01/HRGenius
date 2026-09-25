package com.hrgenius.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Audit log data access (Phase 18). Real SQL paging from day one (the
 * Phase 17 pattern): filters and the page window are computed in the
 * database with a filter-exact count twin, so totals stay honest as the
 * trail grows to any size.
 *
 * Binding rules (the Phase 12 Oracle-mode lesson, applied deliberately):
 *  - only Long / Integer / String binds; no Boolean, no Boolean-flags;
 *  - `pattern` is pre-escaped by the service (SqlPaging.likeEscape) and the
 *    query uses ESCAPE '\\'; a null pattern disables the search clause;
 *  - ACTION / ENTITY_TYPE bind as VARCHAR2 (the enum is stored as STRING).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = """
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:actorId IS NULL OR a.actorId = :actorId)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt < :to)
              AND (:pattern IS NULL
                   OR LOWER(a.entityLabel) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.details) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.actorEmail) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.actorName) LIKE :pattern ESCAPE '\\')
            ORDER BY a.id DESC
            """,
            countQuery = """
            SELECT count(a) FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:actorId IS NULL OR a.actorId = :actorId)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt < :to)
              AND (:pattern IS NULL
                   OR LOWER(a.entityLabel) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.details) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.actorEmail) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.actorName) LIKE :pattern ESCAPE '\\')
            """)
    org.springframework.data.domain.Page<AuditLog> findPaged(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("actorId") Long actorId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("pattern") String pattern,
            Pageable pageable);

    /** Distinct entity families for the filter dropdown (JPQL distinct). */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a ORDER BY a.entityType")
    java.util.List<String> findDistinctEntityTypes();

    /**
     * Full (unpaged) filtered feed for the CSV export — same predicates as
     * {@link #findPaged}, newest first. Spring Data applies the Pageable as
     * a SQL-side LIMIT/OFFSET window, so the export cap (see AuditService)
     * never materializes more rows than allowed.
     */
    @Query(value = """
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:actorId IS NULL OR a.actorId = :actorId)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt < :to)
              AND (:pattern IS NULL
                   OR LOWER(a.entityLabel) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.details) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.actorEmail) LIKE :pattern ESCAPE '\\'
                   OR LOWER(a.actorName) LIKE :pattern ESCAPE '\\')
            ORDER BY a.id DESC
            """)
    java.util.List<AuditLog> findForExport(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("actorId") Long actorId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("pattern") String pattern,
            Pageable pageable);
}
