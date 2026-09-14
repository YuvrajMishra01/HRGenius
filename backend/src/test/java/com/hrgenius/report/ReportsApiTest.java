package com.hrgenius.report;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.hrgenius.auth.LoginRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report exports over real HTTP (Phase 15).
 *
 * Seed-derived expectations (pure reads — this suite creates nothing):
 *  - payroll: last month, 7 payslips (Phase 9/13 verified figure) — PayrollApiTest
 *    runs its payroll-run for the CURRENT month, so last month stays pristine;
 *  - attendance: rows only on days −1..−4 (6+6+2+4+1 = 19) — the attendance
 *    suite writes today and −5d, so the window deliberately excludes both;
 *  - employees and leave rows GROW during the run (OnboardingApiTest creates
 *    EMP008, EmployeeApiTest EMP900, LeaveApiTest adds ~9 requests) —
 *    assertions here are therefore order-tolerant: exact headers, unique-name
 *    searches and filtered shapes, never absolute totals or code prefixes.
 *
 * Runs after the attendance suite alphabetically; nothing later (recruitment)
 * reads the entities this suite would touch — and it touches none.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportsApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static volatile String lastMonth;

    // ------------------------------------------------------------- employees

    @Test
    @Order(1)
    void employeeCsvHasHeaderAllRowsAndExcelSafeEncoding() {
        ResponseEntity<byte[]> res = exchange("/api/v1/reports/employees.csv", adminHeaders());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("employees.csv");

        String body = new String(res.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).startsWith("\ufeff"); // UTF-8 BOM for Excel
        String[] lines = body.split("\r\n");
        assertThat(lines[0]).isEqualTo(
                "\ufeffEmployee Code,Name,Email,Phone,Department,Designation,Employment Type,Status,Joining Date");
        // Count grows across the suite run (onboarding creates EMP008,
        // employees creates EMP900) — assert seed names, not totals/codes.
        assertThat(lines.length).isGreaterThanOrEqualTo(8);
        assertThat(body).contains("Anita Desai");
        assertThat(body).contains("Rohan Kulkarni");
    }

    @Test
    @Order(2)
    void employeeCsvAppliesTheSameFiltersAsTheListEndpoint() {
        // "Rohan" is unique across the whole run (no other suite creates one).
        ResponseEntity<byte[]> emp7 = exchange("/api/v1/reports/employees.csv?search=Rohan", adminHeaders());
        String rohan = new String(emp7.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(rohan.split("\r\n")).hasSize(2);
        assertThat(rohan).contains("Rohan Kulkarni");

        ResponseEntity<byte[]> none = exchange("/api/v1/reports/employees.csv?search=zzz-nobody", adminHeaders());
        String empty = new String(none.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(empty.split("\r\n")).hasSize(1);
        assertThat(empty).contains("Employee Code");
    }

    @Test
    @Order(3)
    void employeePdfIsARealPdfDocument() {
        ResponseEntity<byte[]> res = exchange("/api/v1/reports/employees.pdf", adminHeaders());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("employees.pdf");
        // PDF magic + EOF marker: a structurally valid document, not an error page.
        assertThat(new String(res.getBody(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");
        assertThat(new String(res.getBody(), java.nio.charset.StandardCharsets.ISO_8859_1))
                .contains("%%EOF");
    }

    // -------------------------------------------------------------- payroll

    @Test
    @Order(10)
    void payrollExportsCoverTheSeededPeriod() {
        lastMonth = java.time.LocalDate.now().minusMonths(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-M"));

        ResponseEntity<byte[]> csv = exchange("/api/v1/reports/payroll.csv?year="
                + lastMonth.split("-")[0] + "&month=" + lastMonth.split("-")[1], adminHeaders());
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = new String(csv.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body.split("\r\n")).hasSize(8); // header + 7 payslips
        assertThat(body).contains("EMP001");
        assertThat(body).contains("91250"); // admin's adjusted net
        assertThat(body).contains("33500"); // EMP005's adjusted net

        ResponseEntity<byte[]> pdf = exchange("/api/v1/reports/payroll.pdf?year="
                + lastMonth.split("-")[0] + "&month=" + lastMonth.split("-")[1], adminHeaders());
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");

        // A period with no run exports an empty (header-only) report, not an error.
        ResponseEntity<byte[]> empty = exchange("/api/v1/reports/payroll.csv?year=1999&month=1", adminHeaders());
        assertThat(new String(empty.getBody(), java.nio.charset.StandardCharsets.UTF_8).split("\r\n"))
                .hasSize(1);
    }

    // ----------------------------------------------------------- attendance

    @Test
    @Order(20)
    void attendanceExportsCoverTheSeededWindow() {
        String from = java.time.LocalDate.now().minusDays(4).toString();
        String to = java.time.LocalDate.now().minusDays(1).toString();

        ResponseEntity<byte[]> csv = exchange("/api/v1/reports/attendance.csv?from=" + from + "&to=" + to,
                adminHeaders());
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = new String(csv.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body.split("\r\n")).hasSize(20); // header + 19 seeded rows
        assertThat(body).contains("PRESENT");
        assertThat(body).contains("HALF_DAY");
        assertThat(body).contains("LEAVE");
        assertThat(body).contains("ABSENT");

        ResponseEntity<byte[]> pdf = exchange("/api/v1/reports/attendance.pdf?from=" + from + "&to=" + to,
                adminHeaders());
        assertThat(new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");
    }

    @Test
    @Order(21)
    void attendanceRangeIsValidated() {
        String from = java.time.LocalDate.now().minusDays(1).toString();
        String to = java.time.LocalDate.now().minusDays(4).toString();
        ResponseEntity<byte[]> res = exchange("/api/v1/reports/attendance.csv?from=" + from + "&to=" + to,
                adminHeaders());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --------------------------------------------------------------- leave

    @Test
    @Order(30)
    void leaveExportsIncludeAllSeedRequestsAndSupportStatusFilter() {
        ResponseEntity<byte[]> all = exchange("/api/v1/reports/leave.csv", adminHeaders());
        String body = new String(all.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        // The unfiltered list grows (LeaveApiTest adds requests) — assert the
        // seed content is present, not an absolute count.
        assertThat(body.split("\r\n").length).isGreaterThanOrEqualTo(5);
        assertThat(body).contains("Family function");
        assertThat(body).contains("PENDING");
        assertThat(body).contains("REJECTED");

        ResponseEntity<byte[]> pending = exchange("/api/v1/reports/leave.csv?status=PENDING", adminHeaders());
        String pendingBody = new String(pending.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(pendingBody.split("\r\n").length).isGreaterThanOrEqualTo(3); // header + the 2 seed pending
        assertThat(pendingBody).doesNotContain(",REJECTED,");
        assertThat(pendingBody).doesNotContain(",APPROVED,");

        // Unknown status → 400 (never 500), matching the Phase 14 convention.
        assertThat(exchange("/api/v1/reports/leave.csv?status=NOPE", adminHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<byte[]> pdf = exchange("/api/v1/reports/leave.pdf?status=PENDING", adminHeaders());
        assertThat(new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");
    }

    // ---------------------------------------------------------------- RBAC

    @Test
    @Order(50)
    void exportEndpointsKeepTheReadRoleMatrix() {
        String[] paths = {
                "/api/v1/reports/employees.csv",
                "/api/v1/reports/employees.pdf",
                "/api/v1/reports/leave.csv",
                "/api/v1/reports/leave.pdf",
        };
        for (String path : paths) {
            assertThat(exchange(path, employeeHeaders()).getStatusCode())
                    .as("EMPLOYEE %s", path).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(rest.exchange(url() + path, HttpMethod.GET,
                    new HttpEntity<>(anonymousHeaders()), byte[].class).getStatusCode())
                    .as("ANON %s", path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exchange(path, managerHeaders()).getStatusCode())
                    .as("MANAGER %s", path).isEqualTo(HttpStatus.OK);
        }
    }

    // ------------------------------------------------------------- helpers

    private ResponseEntity<byte[]> exchange(String path, HttpHeaders headers) {
        return rest.exchange(url() + path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private HttpHeaders login(String email, String password) {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(url() + "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password), loginHeaders), String.class);
        assertThat(response.getStatusCode()).as("login %s", email).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        int idx = body.indexOf("\"token\":\"");
        String token = body.substring(idx + 9, body.indexOf('"', idx + 9));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        return login("admin@hrgenius.local", "Admin@123");
    }

    private HttpHeaders managerHeaders() {
        return login("manager@hrgenius.local", "Manager@123");
    }

    private HttpHeaders employeeHeaders() {
        return login("employee@hrgenius.local", "Employee@123");
    }

    private HttpHeaders anonymousHeaders() {
        return new HttpHeaders();
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
