package com.hrgenius.audit;

import java.time.LocalDate;
import java.util.List;

import com.hrgenius.common.ApiResponse;
import com.hrgenius.common.PageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit log API (Phase 18). Strictly HR-facing: ADMIN/HR only — the same
 * policy as the payroll run, but with no MANAGER overlap: employees and
 * managers never receive HR audit data (contains cross-employee detail the
 * read matrix deliberately withholds from them).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    /**
     * Paged, filtered, searched audit feed. Filters are AND-combined:
     * action, entityType, actorId, from/to (inclusive dates), and an
     * escaped search over label / details / actor email.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogDto.AuditLogResponse>>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(
                service.list(action, entityType, actorId, from, to, search, page, size)));
    }

    /** Distinct entity types in the trail — feeds the filter dropdown. */
    @GetMapping("/entity-types")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<List<String>>> entityTypes() {
        return ResponseEntity.ok(ApiResponse.of(service.entityTypes()));
    }
}
