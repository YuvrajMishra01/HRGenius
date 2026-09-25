package com.hrgenius.audit;

import java.time.LocalDate;

import com.hrgenius.report.CsvBuilder;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit CSV export (Phase 19), built on the Phase 15 report machinery:
 * same {@link CsvBuilder}, BOM + Content-Disposition conventions and
 * raw-byte (envelope-less) response style as {@code ReportController}.
 *
 * It lives in the audit package — deliberately NOT under /reports — because
 * the audit read policy is stricter: ADMIN/HR only, never MANAGER. Folding
 * this into ReportController would either widen that guard or need a
 * special case; a separate controller keeps both policies honest.
 *
 * Filters mirror GET /api/v1/audit exactly (action, entityType, actorId,
 * from/to, escaped search) so what you see is what you export; `limit`
 * caps the row count SQL-side (default and ceiling: EXPORT_MAX_ROWS).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditExportController {

    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private static final String BOM = "\ufeff";

    private static final java.util.List<String> HEADERS = java.util.List.of(
            "ID", "Timestamp", "Actor", "Actor Email", "Role", "Action",
            "Entity Type", "Entity ID", "Entity Label", "Details");

    private final AuditService service;

    public AuditExportController(AuditService service) {
        this.service = service;
    }

    @GetMapping("/export.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit) {

        java.util.List<AuditLogDto.AuditLogResponse> rows = service.forExport(
                action, entityType, actorId, from, to, search, limit);

        CsvBuilder csv = new CsvBuilder();
        csv.header(HEADERS);
        for (AuditLogDto.AuditLogResponse r : rows) {
            csv.row(java.util.List.of(
                    r.id() == null ? "" : r.id().toString(),
                    r.createdAt() == null ? "" : r.createdAt(),
                    r.actorName() == null ? "" : r.actorName(),
                    r.actorEmail() == null ? "" : r.actorEmail(),
                    r.actorRole() == null ? "" : r.actorRole(),
                    r.action() == null ? "" : r.action(),
                    r.entityType() == null ? "" : r.entityType(),
                    r.entityId() == null ? "" : r.entityId().toString(),
                    r.entityLabel() == null ? "" : r.entityLabel(),
                    r.details() == null ? "" : r.details()));
        }
        byte[] body = (BOM + csv.build()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return attachment(body, TEXT_CSV, "audit-log.csv");
    }

    // -------------------------------------------------------------- helpers

    private static ResponseEntity<byte[]> attachment(byte[] body, MediaType mediaType, String fileName) {
        String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(mediaType)
                .contentLength(body.length)
                .body(body);
    }
}
