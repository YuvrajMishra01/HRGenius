package com.hrgenius.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.hrgenius.auth.User;
import com.hrgenius.auth.UserRepository;
import com.hrgenius.common.Lists;
import com.hrgenius.common.PageResponse;
import com.hrgenius.common.SqlPaging;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit trail (Phase 18): one service both records important state-changing
 * HR actions and serves the HR/ADMIN-only log view.
 *
 * Recording contract — mirrors the notification pipeline: {@link #record}
 * writes the row in the *caller's* transaction (REQUIRED), so an audit
 * entry commits exactly when the triggering action commits and never
 * describes rolled-back work. It is failure-neutral: it never throws, and
 * it never mutates the outcome the caller is about to return. The action
 * has already been performed and saved when record() runs.
 *
 * The query side pages and filters entirely in SQL (the Phase 17 pattern)
 * with an exact count, so honest totals at any history size.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository users;

    public AuditService(AuditLogRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    /** Hard ceiling for CSV exports; beyond this the API contract says "narrow your filters". */
    public static final int EXPORT_MAX_ROWS = 10_000;

    // ------------------------------------------------------------ recording

    /**
     * Records one completed action in the caller's transaction.
     * Never throws: an audit failure must not fail an already-successful
     * business action, so it cannot poison the caller's commit.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Actor actor, AuditActions action, String entityType, Long entityId,
                       String entityLabel, String details) {
        record(actor == null ? null : actor.id(),
                actor == null ? null : actor.email(),
                actor == null ? null : actor.name(),
                actor == null ? null : actor.role(),
                action, entityType, entityId, entityLabel, details);
    }

    /**
     * Resolves the JWT principal to an actor snapshot. Plain read, no
     * transaction of its own — callers are already inside one.
     */
    public Actor currentActor() {
        return Actor.lookup(users);
    }

    /**
     * Records one completed action in the caller's transaction.
     * Never throws: an audit failure must not fail an already-successful
     * business action, so it cannot poison the caller's commit.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Long actorId, String actorEmail, String actorName, String actorRole,
                       AuditActions action, String entityType, Long entityId,
                       String entityLabel, String details) {
        try {
            AuditLog row = new AuditLog();
            row.setActorId(actorId);
            row.setActorEmail(actorEmail);
            row.setActorName(actorName);
            row.setActorRole(actorRole);
            row.setAction(action.name());
            row.setEntityType(entityType);
            row.setEntityId(entityId);
            row.setEntityLabel(truncate(entityLabel, 200));
            row.setDetails(truncate(details, 1000));
            repository.save(row);
        } catch (RuntimeException e) {
            // Swallow deliberately (never an Error). The business result is
            // already committed-bound; audit is best-effort by design.
        }
    }

    // ------------------------------------------------------------- queries

    /**
     * DB-side paged + filtered audit feed, newest first. All binds are
     * concrete typed values (never Boolean — the Phase 12 hazard); search
     * is LIKE-escaped over label, details, and actor email; `from`/`to`
     * are inclusive day bounds.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto.AuditLogResponse> list(String action, String entityType,
                                                           Long actorId, LocalDate fromDate, LocalDate toDate,
                                                           String search, Integer page, Integer size) {
        String actionFilter = (action == null || action.isBlank()) ? null : action.trim();
        String entityFilter = (entityType == null || entityType.isBlank()) ? null : entityType.trim();
        String pattern = SqlPaging.likeEscapeOrNull(search);
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atTime(LocalTime.MIDNIGHT);
        Pageable pageable = PageRequest.of(Lists.cleanPage(page), Lists.cleanSize(size));
        return SqlPaging.of(repository.findPaged(actionFilter, entityFilter, actorId, from, to,
                        pattern, pageable)
                .map(AuditService::toResponse));
    }

    /** Distinct entity types present in the trail — feeds the filter dropdown. */
    @Transactional(readOnly = true)
    public List<String> entityTypes() {
        return repository.findDistinctEntityTypes();
    }

    /**
     * Full filtered feed for the CSV export (Phase 19) — the same
     * normalization and predicate semantics as {@link #list}, minus paging:
     * every matching row, newest first, capped at {@link #EXPORT_MAX_ROWS}
     * so a stray "export everything" can never OOM the JVM or starve the
     * browser. Newest-first keeps the cap honest: you lose the oldest
     * overflow, not the freshest activity.
     */
    @Transactional(readOnly = true)
    public List<AuditLogDto.AuditLogResponse> forExport(String action, String entityType,
                                                        Long actorId, LocalDate fromDate, LocalDate toDate,
                                                        String search, Integer limit) {
        String actionFilter = (action == null || action.isBlank()) ? null : action.trim();
        String entityFilter = (entityType == null || entityType.isBlank()) ? null : entityType.trim();
        String pattern = SqlPaging.likeEscapeOrNull(search);
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atTime(LocalTime.MIDNIGHT);
        // The cap rides along as a SQL LIMIT (unpaged query + Pageable), so
        // the database — not the JVM — enforces it.
        Pageable cap = PageRequest.of(0, exportLimit(limit));
        return repository.findForExport(actionFilter, entityFilter, actorId, from, to, pattern, cap)
                .stream()
                .map(AuditService::toResponse)
                .toList();
    }

    /** Export row cap: null/invalid → default (10k); clamped to [1, EXPORT_MAX_ROWS]. */
    public static int exportLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return EXPORT_MAX_ROWS;
        }
        return Math.min(limit, EXPORT_MAX_ROWS);
    }

    // ------------------------------------------------------------- helpers

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static AuditLogDto.AuditLogResponse toResponse(AuditLog row) {
        DateTimeFormatter ts = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return new AuditLogDto.AuditLogResponse(
                row.getId(),
                row.getActorId(),
                row.getActorEmail(),
                row.getActorName(),
                row.getActorRole(),
                row.getAction(),
                row.getEntityType(),
                row.getEntityId(),
                row.getEntityLabel(),
                row.getDetails(),
                row.getCreatedAt() == null ? null : row.getCreatedAt().format(ts));
    }

    // ------------------------------------------------------------ principal

    /**
     * JWT principal → denormalized actor snapshot. Null-safe for every
     * field: if no principal or no matching user exists, the audit row is
     * still written with whatever identity is available.
     */
    public record Actor(Long id, String email, String name, String role) {

        public static Actor current() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UserDetails details)) {
                return new Actor(null, null, null, null);
            }
            return new Actor(null, details.getUsername(), null, null);
        }

        public static Actor of(User user) {
            if (user == null) {
                return Actor.current();
            }
            return new Actor(user.getId(), user.getEmail(), user.getFullName(),
                    user.getRole() == null ? null : user.getRole().name());
        }

        public static Actor lookup(UserRepository users) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UserDetails details)) {
                return new Actor(null, null, null, null);
            }
            return users.findByEmailIgnoreCase(details.getUsername())
                    .map(Actor::of)
                    .orElseGet(() -> new Actor(null, details.getUsername(), null, null));
        }
    }
}
