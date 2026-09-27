package com.hrgenius.concurrency;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import com.hrgenius.attendance.Attendance;
import com.hrgenius.attendance.AttendanceDto;
import com.hrgenius.auth.LoginRequest;
import com.hrgenius.leave.LeaveDto;
import com.hrgenius.leave.LeaveRequest;
import com.hrgenius.payroll.PayrollDto;
import com.hrgenius.performance.PerformanceDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 21 — parallel duplicate-request storms over real HTTP.
 *
 * Every test fires N identical (or index-varying) requests at the same instant
 * and asserts that at most one business effect occurs. Guards under test:
 *
 *  - payroll run: UK_PAY_EMP_PERIOD — concurrent runs each see an empty
 *    period (the pre-check is advisory under concurrency), so the losers must
 *    surface as clean 409s via the unique constraint, never 500, and the
 *    period must hold exactly one payslip per employee;
 *  - leave decision: PENDING → decided read-modify-write is guarded by the
 *    @Version optimistic lock (V7) — exactly one of approve/reject wins, the
 *    rest see 409, and balances reflect exactly one decision;
 *  - review creation: UK_PR_EMP_PERIOD (V6) — duplicates blocked at the DB;
 *  - attendance mark: per-employee SELECT ... FOR UPDATE (Phase 21) with
 *    UK_ATT_EMP_DATE as backstop — concurrent marks serialize into one row;
 *  - leave submission: the same per-employee lock serializes the overlap +
 *    balance checks, so identical concurrent submissions cannot all pass.
 *
 * Suite interplay (alphabetical order: concurrency runs BEFORE leave/
 * notification/onboarding/payroll/performance): every row created here is
 * cleaned up (cancelled or deleted) or written to windows no other suite
 * asserts (d-9/d-10 attendance, 2099-H1 review). Notification tests use
 * relative unread deltas; LeaveApiTest expects exactly the 2 seed PENDING rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrencyApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final int STORM = 5;
    private static final String MARKER = "Phase 21 concurrency";

    // ------------------------------------------------------- 1. payroll run

    @Test
    @Order(1)
    void concurrentPayrollRunsCreatePayslipsExactlyOnce() throws Exception {
        LocalDate now = LocalDate.now();
        PayrollDto.RunRequest request = new PayrollDto.RunRequest(now.getYear(), now.getMonthValue());

        List<ResponseEntity<String>> responses = storm(
                () -> exchange(HttpMethod.POST, "/api/v1/payrolls/run", hrHeaders(), request));

        // Every response is a clean success or a clean conflict — a loser that
        // hits the unique constraint must never surface as a 500.
        for (ResponseEntity<String> r : responses) {
            assertThat(r.getStatusCode().value())
                    .as("status %s", r.getBody()).isIn(201, 409);
        }
        assertThat(responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count())
                .as("at least one run must succeed").isGreaterThanOrEqualTo(1);

        // The period holds exactly one payslip per employee — no doubles.
        ResponseEntity<String> period = exchange(HttpMethod.GET,
                "/api/v1/payrolls/" + now.getYear() + "/" + now.getMonthValue(), adminHeaders(), null);
        assertThat(period.getStatusCode()).isEqualTo(HttpStatus.OK);

        Set<Long> employeeIds = new HashSet<>();
        int rows = 0;
        for (String chunk : period.getBody().split("\\{\"id\":")) {
            if (!chunk.contains("\"employeeId\":")) {
                continue;
            }
            int start = chunk.indexOf("\"employeeId\":") + "\"employeeId\":".length();
            employeeIds.add(Long.parseLong(chunk.substring(start, chunk.indexOf(',', start))));
            rows++;
        }
        assertThat(rows).as("payslips created").isGreaterThanOrEqualTo(1);
        assertThat(rows).as("one row per employee — no duplicates")
                .isEqualTo(employeeIds.size());

        // Cleanup: remove the storm's DRAFT payslips so PayrollApiTest (later
        // alphabetically) still creates rows for the current month. Only PAID
        // rows are undeletable — these are all DRAFT.
        for (String chunk : period.getBody().split("\\{\"id\":")) {
            if (!chunk.contains("\"employeeId\":")) {
                continue;
            }
            long id = Long.parseLong(chunk.substring(0, chunk.indexOf(',')));
            exchange(HttpMethod.DELETE, "/api/v1/payrolls/" + id, hrHeaders(), null);
        }
    }

    // -------------------------------------------- 2. leave double-decision

    @Test
    @Order(2)
    void concurrentApproveAndRejectDecideExactlyOnce() throws Exception {
        // Employee 7 (Rohan) has no live leave anywhere — nothing overlaps.
        // A single weekday → workingDays is exactly 1.0.
        LocalDate day = LocalDate.now().plusDays(21);
        while (day.getDayOfWeek().getValue() > 5) {
            day = day.plusDays(1);
        }
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(7L, 1L, day, day, MARKER + " decision"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = extractId(created.getBody());

        // Half the storm approves, half rejects — exactly one may win; the
        // winner is whichever transaction commits first.
        List<ResponseEntity<String>> responses = storm(i -> exchange(HttpMethod.PATCH,
                "/api/v1/leave/requests/" + id + (i % 2 == 0 ? "/approve" : "/reject"), hrHeaders(), null));

        long wins = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long losses = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
        assertThat(wins).as("exactly one decision wins").isEqualTo(1);
        assertThat(wins + losses).isEqualTo(STORM);
        for (ResponseEntity<String> r : responses) {
            assertThat(r.getStatusCode().value()).as("status %s", r.getBody()).isIn(200, 409);
        }

        // Final state is exactly one decided status — the loser's action did
        // not overwrite the winner (that is the @Version guard).
        ResponseEntity<String> list = exchange(HttpMethod.GET, "/api/v1/leave/requests?size=100", adminHeaders(), null);
        String row = rowById(list.getBody(), id);
        boolean approvedWon = row.contains("\"status\":\"APPROVED\"");
        assertThat(approvedWon || row.contains("\"status\":\"REJECTED\"")).isTrue();

        // The balance reflects exactly ONE decision: an approval deducts 1.0
        // of CASUAL (12), a rejection deducts nothing — a double-apply would
        // show 2.0 used or a phantom deduction after a rejection.
        ResponseEntity<String> balances = exchange(HttpMethod.GET, "/api/v1/leave/balances/7", adminHeaders(), null);
        if (approvedWon) {
            assertThat(balances.getBody()).contains("\"usedDays\":1.0,\"remainingDays\":11.0");
        } else {
            assertThat(balances.getBody()).contains("\"usedDays\":0.0,\"remainingDays\":12.0");
        }

        // Cleanup: decided rows are deletable (only PENDING is protected).
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/leave/requests/" + id, hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ---------------------------------------- 3. duplicate review creation

    @Test
    @Order(3)
    void concurrentDuplicateReviewsAreConstraintBlocked() throws Exception {
        PerformanceDto.CreateRequest request = new PerformanceDto.CreateRequest(
                4L, 1L, "2099-H1", MARKER);
        List<ResponseEntity<String>> responses = storm(() -> exchange(HttpMethod.POST,
                "/api/v1/performance/reviews", hrHeaders(), request));

        long wins = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        long conflicts = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
        assertThat(wins).isEqualTo(1);
        assertThat(conflicts).isEqualTo(STORM - 1);
        for (ResponseEntity<String> r : responses) {
            assertThat(r.getStatusCode().value()).isIn(201, 409);
        }

        // Cleanup: the winner is DRAFT → deletable.
        long id = extractId(responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CREATED).findFirst().orElseThrow().getBody());
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/performance/reviews/" + id, hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ------------------------------------------- 4. attendance mark storms

    @Test
    @Order(4)
    void concurrentMarksCollapseIntoOneRow() throws Exception {
        // d-10 for Vikram (3): outside every window other suites assert
        // (reports exports d-4..d-1; the attendance suite writes today/d-5 and
        // runs later anyway).
        LocalDate day = LocalDate.now().minusDays(10);
        List<ResponseEntity<String>> responses = storm(() -> exchange(HttpMethod.POST, "/api/v1/attendance/mark",
                hrHeaders(), new AttendanceDto.MarkRequest(3L, day, Attendance.AttendanceStatus.PRESENT)));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));

        // The hard invariant, proven at the database: exactly ONE row exists
        // for the employee/day — the per-employee lock serialized the
        // find-then-upsert and UK_ATT_EMP_DATE is the backstop.
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from attendance where employee_id = 3 and attendance_date = ?",
                Integer.class, day);
        assertThat(rows).as("exactly one attendance row").isEqualTo(1);
        String status = jdbcTemplate.queryForObject(
                "select status from attendance where employee_id = 3 and attendance_date = ?",
                String.class, day);
        assertThat(status).isEqualTo("PRESENT");

        // And the month view agrees: the day key carries the final status.
        assertThat(monthRowFor(day, "Vikram Singh"))
                .contains("\"" + day.getDayOfMonth() + "\":\"PRESENT\"");
    }

    @Test
    @Order(5)
    void concurrentMixedStatusMarksEndInExactlyOneStatus() throws Exception {
        // Five different statuses fired at once on the same employee/day.
        // Responses may legitimately echo different intermediate states (each
        // mirrors its own serialized write), so the invariant is asserted on
        // the final row: exactly one status bucket holds the single record.
        LocalDate day = LocalDate.now().minusDays(9);
        Attendance.AttendanceStatus[] statuses = {
                Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.ABSENT,
                Attendance.AttendanceStatus.HALF_DAY, Attendance.AttendanceStatus.LEAVE,
                Attendance.AttendanceStatus.HOLIDAY };
        List<ResponseEntity<String>> responses = storm(i -> exchange(HttpMethod.POST, "/api/v1/attendance/mark",
                hrHeaders(), new AttendanceDto.MarkRequest(3L, day, statuses[i % statuses.length])));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));

        // Serialization point proven at the database: exactly ONE row, and its
        // status is exactly one of the five fired — never a blend, never a
        // duplicate. (Each response mirrors its own serialized write; with a
        // storm the last writer's echo is the final state, which is correct
        // upsert semantics.)
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from attendance where employee_id = 3 and attendance_date = ?",
                Integer.class, day);
        assertThat(rows).as("exactly one attendance row").isEqualTo(1);
        String status = jdbcTemplate.queryForObject(
                "select status from attendance where employee_id = 3 and attendance_date = ?",
                String.class, day);
        assertThat(status).isIn("PRESENT", "ABSENT", "HALF_DAY", "LEAVE", "HOLIDAY");

        assertThat(monthRowFor(day, "Vikram Singh"))
                .contains("\"" + day.getDayOfMonth() + "\":\"" + status + "\"");
    }

    // -------------------------------------- 5. duplicate leave submission

    @Test
    @Order(6)
    void concurrentIdenticalSubmissionsCannotAllPassTheOverlapCheck() throws Exception {
        // Employee 6 (Divya), far-future weekday — no overlap with any seed or
        // other suite's rows. The per-employee lock serializes the overlap +
        // balance checks, so the FIRST submission wins and every later one is
        // a clean 409 — deterministic, not probabilistic.
        LocalDate start = LocalDate.now().plusDays(16);
        while (start.getDayOfWeek().getValue() > 5) {
            start = start.plusDays(1);
        }
        LeaveDto.CreateLeaveRequest request = new LeaveDto.CreateLeaveRequest(
                6L, 1L, start, start, MARKER + " submission");
        List<ResponseEntity<String>> responses = storm(() -> exchange(HttpMethod.POST,
                "/api/v1/leave/requests", hrHeaders(), request));

        long wins = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        long conflicts = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
        assertThat(wins).as("the lock makes exactly one submission win").isEqualTo(1);
        assertThat(conflicts).isEqualTo(STORM - 1);
        for (ResponseEntity<String> r : responses) {
            assertThat(r.getStatusCode().value()).as("status %s", r.getBody()).isIn(201, 409);
        }

        // Cleanup: cancel the winner so LeaveApiTest still sees exactly the
        // 2 seed PENDING rows.
        long id = extractId(responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CREATED).findFirst().orElseThrow().getBody());
        assertThat(exchange(HttpMethod.PATCH, "/api/v1/leave/requests/" + id + "/cancel", hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------- helpers

    private interface StormTask {
        ResponseEntity<String> run(int attempt) throws Exception;
    }

    /** Fires STORM requests, released together by a latch. */
    private List<ResponseEntity<String>> storm(StormTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(STORM);
        try {
            CountDownLatch startLine = new CountDownLatch(1);
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < STORM; i++) {
                final int attempt = i;
                futures.add(pool.submit(() -> {
                    startLine.await();
                    return task.run(attempt);
                }));
            }
            startLine.countDown();
            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<ResponseEntity<String>> storm(Callable<ResponseEntity<String>> task) throws Exception {
        return storm(i -> task.call());
    }

    /** The month-rollup JSON fragment for one employee on the given day's month. */
    private String monthRowFor(LocalDate day, String employeeName) {
        ResponseEntity<String> month = exchange(HttpMethod.GET,
                "/api/v1/attendance/month?year=" + day.getYear() + "&month=" + day.getMonthValue(),
                adminHeaders(), null);
        assertThat(month.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = month.getBody();
        int at = body.indexOf(employeeName);
        assertThat(at).as("%s present in month view", employeeName).isGreaterThanOrEqualTo(0);
        int end = body.indexOf('}', at);
        return body.substring(at, end);
    }

    private long extractId(String body) {
        int idx = body.indexOf("\"id\":");
        return Long.parseLong(body.substring(idx + 5, body.indexOf(',', idx)));
    }

    private String rowById(String body, long id) {
        for (String chunk : body.split("\\{\"id\":")) {
            if (chunk.startsWith(id + ",")) {
                return chunk.substring(0, chunk.indexOf('}'));
            }
        }
        return "";
    }

    private HttpHeaders hrHeaders() {
        return bearer("hr@hrgenius.local", "Hr@12345");
    }

    private HttpHeaders adminHeaders() {
        return bearer("admin@hrgenius.local", "Admin@123");
    }

    private HttpHeaders bearer(String email, String password) {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.exchange(url() + "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password), loginHeaders), String.class);
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
