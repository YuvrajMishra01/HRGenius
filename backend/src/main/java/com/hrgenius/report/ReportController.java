package com.hrgenius.report;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report exports (Phase 15). CSV and PDF downloads for the four main HR
 * lists; ADMIN/HR/MANAGER can read every list, so the same guard applies
 * here. Responses are raw bytes — deliberately outside the ApiResponse
 * envelope so the browser saves a real file.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final MediaType APPLICATION_PDF = MediaType.parseMediaType("application/pdf");
    private static final String BOM = "\ufeff";

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    // ----------------------------------------------------------- employees

    @GetMapping("/employees.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<byte[]> employeesCsv(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) String status) {
        return csv("employees", ReportService.EMPLOYEE_HEADERS,
                service.employeeRows(search, status));
    }

    @GetMapping("/employees.pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<byte[]> employeesPdf(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) String status) {
        return pdf("Employee Directory", "All employees matching the current filters",
                ReportService.EMPLOYEE_HEADERS, service.employeeRows(search, status), "employees");
    }

    // ------------------------------------------------------------- payroll

    @GetMapping("/payroll.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<byte[]> payrollCsv(@RequestParam int year, @RequestParam int month) {
        return csv("payroll-" + year + "-" + String.format("%02d", month),
                ReportService.PAYROLL_HEADERS, service.payrollRows(year, month));
    }

    @GetMapping("/payroll.pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<byte[]> payrollPdf(@RequestParam int year, @RequestParam int month) {
        return pdf("Payroll Report", periodLabel(year, month),
                ReportService.PAYROLL_HEADERS, service.payrollRows(year, month),
                "payroll-" + year + "-" + String.format("%02d", month));
    }

    // ----------------------------------------------------------- attendance

    @GetMapping("/attendance.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<byte[]> attendanceCsv(@RequestParam String from, @RequestParam String to) {
        LocalDate[] range = range(from, to);
        return csv("attendance-" + range[0] + "-" + range[1],
                ReportService.ATTENDANCE_HEADERS, service.attendanceRows(range[0], range[1]));
    }

    @GetMapping("/attendance.pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<byte[]> attendancePdf(@RequestParam String from, @RequestParam String to) {
        LocalDate[] range = range(from, to);
        return pdf("Attendance Report", range[0] + " to " + range[1],
                ReportService.ATTENDANCE_HEADERS, service.attendanceRows(range[0], range[1]),
                "attendance-" + range[0] + "-" + range[1]);
    }

    // --------------------------------------------------------------- leave

    @GetMapping("/leave.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<byte[]> leaveCsv(@RequestParam(required = false) String status) {
        return csv("leave" + (status == null || status.isBlank() ? "" : "-" + status.toLowerCase()),
                ReportService.LEAVE_HEADERS, service.leaveRows(status));
    }

    @GetMapping("/leave.pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<byte[]> leavePdf(@RequestParam(required = false) String status) {
        boolean filtered = status != null && !status.isBlank();
        return pdf("Leave Report", filtered ? "Status: " + status.toUpperCase() : "All requests",
                ReportService.LEAVE_HEADERS, service.leaveRows(status),
                "leave" + (filtered ? "-" + status.toLowerCase() : ""));
    }

    // -------------------------------------------------------------- helpers

    /** Validates and parses the attendance range; end must not precede start. */
    private static LocalDate[] range(String from, String to) {
        LocalDate start = LocalDate.parse(from);
        LocalDate end = LocalDate.parse(to);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        return new LocalDate[] { start, end };
    }

    private static String periodLabel(int year, int month) {
        return String.format("%04d-%02d", year, month);
    }

    private static ResponseEntity<byte[]> csv(String baseName, List<String> headers,
                                              List<List<String>> rows) {
        CsvBuilder csv = new CsvBuilder();
        csv.header(headers);
        rows.forEach(csv::row);
        byte[] body = (BOM + csv.build()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return attachment(body, TEXT_CSV, baseName + ".csv");
    }

    private static ResponseEntity<byte[]> pdf(String title, String subtitle, List<String> headers,
                                              List<List<String>> rows, String baseName) {
        byte[] body = ReportPdfBuilder.build(title, subtitle + " · generated " + ReportService.stamp(),
                headers, rows);
        return attachment(body, APPLICATION_PDF, baseName + ".pdf");
    }

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
