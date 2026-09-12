package com.hrgenius.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payroll over real HTTP against the seeded H2 database (Phase 9).
 *
 * Class order is alphabetical (junit-platform.properties): … employee →
 * leave → onboarding → payroll → recruitment. Earlier suites may have added
 * ACTIVE employees (onboarding conversions), so this suite asserts only
 * RELATIVE counts (run twice → second run creates nothing) and data-driven
 * prefill (a draft's components equal that employee's latest payslip, with
 * net recomputed). No other suite reads PAYROLLS, so mutations here are safe.
 *
 * Seed baseline: employees 1–6 have PROCESSED payslips for last month
 * (e.g. EMP001: 85000 + 12000 − 2500 − 8250 → recomputed net 86250, while
 * the seed's stored net 91250 is deliberately inconsistent — proving the
 * run recomputes instead of copying).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PayrollApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static volatile PayrollDto.RunResponse firstRun;
    private static volatile int currentYear;
    private static volatile int currentMonth;
    private static volatile Long targetPayslipId;
    private static volatile Long secondDraftId;

    // ------------------------------------------------------------- run

    @Test
    @Order(1)
    void runCreatesDraftPayslipsForActiveEmployees() {
        LocalDate now = LocalDate.now();
        currentYear = now.getYear();
        currentMonth = now.getMonthValue();

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/payrolls/run", hrHeaders(),
                new PayrollDto.RunRequest(currentYear, currentMonth));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();

        assertThat(body).contains("\"created\":");
        assertThat(body).contains("\"skipped\":");
        firstRun = new PayrollDto.RunResponse(
                currentYear, currentMonth,
                intField(body, "created"),
                intField(body, "skipped"),
                intField(body, "periodPayslips"),
                BigDecimal.ZERO);
        assertThat(firstRun.created()).isGreaterThanOrEqualTo(1);
        assertThat(firstRun.periodPayslips()).isGreaterThanOrEqualTo(firstRun.created());
    }

    @Test
    @Order(2)
    void secondRunCreatesNothingAndSkipsEveryone() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/payrolls/run", hrHeaders(),
                new PayrollDto.RunRequest(currentYear, currentMonth));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        int created2 = intField(response.getBody(), "created");
        int skipped2 = intField(response.getBody(), "skipped");
        assertThat(created2).isZero();
        // Everyone ACTIVE now has a row: skipped = run-1 created + skipped
        assertThat(skipped2).isEqualTo(firstRun.created() + firstRun.skipped());
    }

    // ------------------------------------------------------------- views

    @Test
    @Order(10)
    void periodViewShowsDraftsAndPrefillFromLatestPayslip() {
        ResponseEntity<String> current = exchange(HttpMethod.GET,
                "/api/v1/payrolls/" + currentYear + "/" + currentMonth, managerHeaders(), null);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = current.getBody();

        // Only run-1 rows exist for this period, all DRAFT
        assertThat(intField(body, "draftCount")).isEqualTo(firstRun.created());
        assertThat(body).contains("\"status\":\"DRAFT\"");

        // Prefill: find a payslip whose employee had a last-month row
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        ResponseEntity<String> previous = exchange(HttpMethod.GET,
                "/api/v1/payrolls/" + lastMonth.getYear() + "/" + lastMonth.getMonthValue(),
                managerHeaders(), null);
        assertThat(previous.getStatusCode()).isEqualTo(HttpStatus.OK);

        String[] rows = body.split("\\{\"id\":");
        for (String row : rows) {
            String code = extract(row, "\"employeeCode\":\"([^\"]+)\"");
            if (code == null) {
                continue;
            }
            Matcher prevBasic = Pattern.compile("\\{\"id\":.*\"employeeCode\":\"" + code
                    + "\".*?\"basicSalary\":([0-9.]+)").matcher("");
            Matcher inPrev = Pattern.compile("\"employeeCode\":\"" + code + "\".*?\"basicSalary\":([0-9.]+)")
                    .matcher(previous.getBody());
            if (!inPrev.find()) {
                continue;
            }
            BigDecimal prevBasicValue = new BigDecimal(inPrev.group(1));
            BigDecimal newBasic = new BigDecimal(extract(row, "\"basicSalary\":([0-9.]+)"));
            assertThat(newBasic.compareTo(prevBasicValue)).as("prefill for %s", code).isZero();

            BigDecimal prevAllow = new BigDecimal(extractAfter(previous.getBody(),
                    "\"employeeCode\":\"" + code + "\"", "\"allowances\":([0-9.]+)"));
            BigDecimal prevDed = new BigDecimal(extractAfter(previous.getBody(),
                    "\"employeeCode\":\"" + code + "\"", "\"deductions\":([0-9.]+)"));
            BigDecimal prevTax = new BigDecimal(extractAfter(previous.getBody(),
                    "\"employeeCode\":\"" + code + "\"", "\"tax\":([0-9.]+)"));
            BigDecimal recomputed = prevBasicValue.add(prevAllow).subtract(prevDed).subtract(prevTax).max(BigDecimal.ZERO);
            BigDecimal newNet = new BigDecimal(extract(row, "\"netSalary\":([0-9.]+)"));
            assertThat(newNet.compareTo(recomputed)).as("recomputed net for %s", code).isZero();

            if (targetPayslipId == null) {
                targetPayslipId = Long.parseLong(row.substring(0, row.indexOf(',')));
            } else if (secondDraftId == null) {
                secondDraftId = Long.parseLong(row.substring(0, row.indexOf(',')));
            }
            if (targetPayslipId != null && secondDraftId != null) {
                return;
            }
        }
        org.assertj.core.api.Assertions.fail("Expected at least two prefilled draft payslips");
    }

    @Test
    @Order(11)
    void periodsOverviewListsAllPeriodsWithTotals() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/payrolls", managerHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"year\":" + currentYear);
        assertThat(body).contains("\"month\":" + currentMonth);
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        assertThat(body).contains("\"year\":" + lastMonth.getYear());
        assertThat(body).contains("\"month\":" + lastMonth.getMonthValue());
    }

    // --------------------------------------------------------- lifecycle

    @Test
    @Order(20)
    void componentUpdateRecomputesNetWithBigDecimalMath() {
        PayrollDto.ComponentUpdateRequest update =
                new PayrollDto.ComponentUpdateRequest(
                        new BigDecimal("60123.45"), new BigDecimal("5000.10"),
                        new BigDecimal("2000.20"), new BigDecimal("3000.30"));
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/payrolls/" + targetPayslipId + "/components", hrHeaders(), update);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 60123.45 + 5000.10 − 2000.20 − 3000.30 = 60123.05 (exact 2-dp math)
        assertThat(response.getBody()).contains("\"netSalary\":60123.05");
        assertThat(response.getBody()).contains("\"basicSalary\":60123.45");
    }

    @Test
    @Order(21)
    void negativeComponentsAreRejected() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/payrolls/" + targetPayslipId + "/components", hrHeaders(),
                "{\"basicSalary\":-1,\"allowances\":0,\"deductions\":0,\"tax\":0}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"errors\"");
    }

    @Test
    @Order(22)
    void unknownPayslipIs404() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/payrolls/99999/components", hrHeaders(),
                new PayrollDto.ComponentUpdateRequest(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(23)
    void processThenPayLifecycleWithGuards() {
        ResponseEntity<String> processed = exchange(HttpMethod.PATCH,
                "/api/v1/payrolls/" + targetPayslipId + "/process", hrHeaders(), null);
        assertThat(processed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(processed.getBody()).contains("\"status\":\"PROCESSED\"");

        // DRAFT → PROCESSED is one-way; re-processing is a 409
        assertThat(exchange(HttpMethod.PATCH, "/api/v1/payrolls/" + targetPayslipId + "/process", hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> paid = exchange(HttpMethod.PATCH,
                "/api/v1/payrolls/" + targetPayslipId + "/pay", hrHeaders(), null);
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paid.getBody()).contains("\"status\":\"PAID\"");

        // PAID is immutable: no edits, no re-pay, no delete
        assertThat(exchange(HttpMethod.PATCH, "/api/v1/payrolls/" + targetPayslipId + "/pay", hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<String> editPaid = exchange(HttpMethod.PATCH,
                "/api/v1/payrolls/" + targetPayslipId + "/components", hrHeaders(),
                new PayrollDto.ComponentUpdateRequest(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(editPaid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(editPaid.getBody()).contains("PAID payslips cannot be edited");

        ResponseEntity<String> deletePaid = exchange(HttpMethod.DELETE,
                "/api/v1/payrolls/" + targetPayslipId, hrHeaders(), null);
        assertThat(deletePaid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deletePaid.getBody()).contains("permanent");
    }

    @Test
    @Order(24)
    void draftCanBeDeleted() {
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/payrolls/" + secondDraftId, hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(exchange(HttpMethod.GET,
                "/api/v1/payrolls/" + currentYear + "/" + currentMonth, managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------- validation

    @Test
    @Order(30)
    void futurePeriodIsRejected() {
        LocalDate next = LocalDate.now().plusMonths(1);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/payrolls/run", hrHeaders(),
                new PayrollDto.RunRequest(next.getYear(), next.getMonthValue()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("future period");
    }

    @Test
    @Order(31)
    void invalidMonthIsRejected() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/payrolls/run", hrHeaders(),
                new PayrollDto.RunRequest(currentYear, 13));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"errors\"");
    }

    // -------------------------------------------------------------- RBAC

    @Test
    @Order(40)
    void employeeForbiddenManagerReadsButCannotWrite() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/payrolls", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.GET,
                "/api/v1/payrolls/" + currentYear + "/" + currentMonth, managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(exchange(HttpMethod.POST, "/api/v1/payrolls/run", managerHeaders(),
                new PayrollDto.RunRequest(currentYear, currentMonth)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.GET, "/api/v1/payrolls", adminHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------- helpers

    private static int intField(String body, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":(\\d+)").matcher(body);
        assertThat(m.find()).as("field %s in %s", field, body).isTrue();
        return Integer.parseInt(m.group(1));
    }

    private static String extract(String body, String regex) {
        Matcher m = Pattern.compile(regex).matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static String extractAfter(String body, String anchor, String regex) {
        int at = body.indexOf(anchor);
        assertThat(at).as("anchor %s", anchor).isGreaterThanOrEqualTo(0);
        Matcher m = Pattern.compile(regex).matcher(body.substring(at));
        assertThat(m.find()).as("%s after %s", regex, anchor).isTrue();
        return m.group(1);
    }

    private HttpHeaders adminHeaders() {
        return bearer("admin@hrgenius.local", "Admin@123");
    }

    private HttpHeaders hrHeaders() {
        return bearer("hr@hrgenius.local", "Hr@12345");
    }

    private HttpHeaders managerHeaders() {
        return bearer("manager@hrgenius.local", "Manager@123");
    }

    private HttpHeaders employeeHeaders() {
        return bearer("employee@hrgenius.local", "Employee@123");
    }

    private HttpHeaders bearer(String email, String password) {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.exchange(url() + "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new com.hrgenius.auth.LoginRequest(email, password), loginHeaders), String.class);
        assertThat(login.getStatusCode()).as("login %s", email).isEqualTo(HttpStatus.OK);
        String body = login.getBody();
        int idx = body.indexOf("\"token\":\"");
        String token = body.substring(idx + 9, body.indexOf('"', idx + 9));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, HttpHeaders headers, Object body) {
        return rest.exchange(url() + path, method, new HttpEntity<>(body, headers), String.class);
    }

    private String url() {
        return "http://localhost:" + port;
    }
}
