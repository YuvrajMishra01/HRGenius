package com.hrgenius.leave;

import java.time.DayOfWeek;
import java.time.LocalDate;

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
 * Leave management over real HTTP against the seeded H2 database (Phase 8).
 *
 * Class order is alphabetical (junit-platform.properties): analytics →
 * attendance → auth → department → employee → leave → onboarding →
 * recruitment. No earlier suite touches LEAVE_REQUESTS, so this suite starts
 * from the pure seed: 4 requests — ids 1 (PENDING, emp 2, CASUAL), 2 (PENDING,
 * emp 3, SICK), 3 (APPROVED, emp 5, CASUAL, 2 days), 4 (REJECTED, emp 4).
 * Later suites never read leave, so mutations here are safe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LeaveApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    /** Id of the request created in order 1, reused by the approve test. */
    private static volatile Long createdRequestId;

    // ------------------------------------------------------------- create

    @Test
    @Order(1)
    void createRequestDefaultsToPendingAndCountsWorkingDays() {
        LocalDate start = nextMonday(LocalDate.now().plusWeeks(2));
        LocalDate end = start.plusDays(4); // Mon–Fri → 5 working days
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(2L, 1L, start, end, "Family function"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = response.getBody();
        assertThat(body).contains("\"status\":\"PENDING\"");
        assertThat(body).contains("\"workingDays\":5.0");
        assertThat(body).contains("Anita Desai");
        assertThat(body).contains("\"leaveTypeName\":\"CASUAL_LEAVE\"");
        createdRequestId = extractId(body);
        assertThat(createdRequestId).isNotNull();
    }

    @Test
    @Order(2)
    void overlappingRequestIs409() {
        // Employee 2 now has a PENDING request in 2 weeks; overlapping range → 409
        LocalDate start = nextMonday(LocalDate.now().plusWeeks(2)).plusDays(2);
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(2L, 1L, start, start.plusDays(1), "Overlap attempt"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("overlapping");
    }

    @Test
    @Order(3)
    void endBeforeStartIs400() {
        LocalDate start = nextMonday(LocalDate.now().plusWeeks(3));
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(3L, 2L, start, start.minusDays(1), "Bad range"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("End date cannot be before start date");
    }

    @Test
    @Order(4)
    void unknownEmployeeOrTypeIs404() {
        LocalDate start = nextMonday(LocalDate.now().plusWeeks(4));
        assertThat(exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(9999L, 1L, start, start.plusDays(1), "?"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(3L, 9999L, start, start.plusDays(1), "?"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------- types

    @Test
    @Order(10)
    void createDuplicateTypeIs409AndDeleteUnusedTypeSucceeds() {
        ResponseEntity<String> duplicate = exchange(HttpMethod.POST, "/api/v1/leave/types", hrHeaders(),
                new LeaveDto.LeaveTypeRequest("casual_leave", "Duplicate of seed", 12));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("already exists");

        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/leave/types", hrHeaders(),
                new LeaveDto.LeaveTypeRequest("Sabbatical", "Long-term break", 30));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"name\":\"Sabbatical\"");
        assertThat(created.getBody()).contains("\"yearlyLimit\":30");

        Long typeId = extractId(created.getBody());
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/leave/types/" + typeId, hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(11)
    void deleteTypeWithRequestsIs409() {
        // CASUAL (type 1) has seed + newly created requests
        ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/api/v1/leave/types/1", hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Cannot delete a leave type that has requests");
    }

    // ----------------------------------------------------------- approval

    @Test
    @Order(20)
    void approveSetsStatusAndApprover() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/leave/requests/" + createdRequestId + "/approve", hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"status\":\"APPROVED\"");
        assertThat(body).contains("\"approverEmail\":\"hr@hrgenius.local\"");
    }

    @Test
    @Order(21)
    void approvingNonPendingIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/leave/requests/" + createdRequestId + "/approve", hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Only PENDING requests can be approved");
    }

    @Test
    @Order(22)
    void rejectSetsStatus() {
        // Fresh request for employee 3 (SICK), then reject it
        LocalDate start = nextMonday(LocalDate.now().plusWeeks(5));
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(3L, 2L, start, start.plusDays(2), "Will reject this"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = extractId(created.getBody());

        ResponseEntity<String> response = exchange(HttpMethod.PATCH,
                "/api/v1/leave/requests/" + id + "/reject", adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"REJECTED\"");
        assertThat(response.getBody()).contains("\"approverEmail\":\"admin@hrgenius.local\"");
    }

    @Test
    @Order(23)
    void balanceExhaustionIs409() {
        // Employee 2 CASUAL: 5 approved (order 20) of 12 → requesting 10 more weekdays must fail
        LocalDate start = nextMonday(LocalDate.now().plusWeeks(8));
        LocalDate end = start;
        int working = 0;
        while (working < 10) {
            end = end.plusDays(1);
            if (end.getDayOfWeek() != DayOfWeek.SATURDAY && end.getDayOfWeek() != DayOfWeek.SUNDAY) {
                working++;
            }
        }
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(2L, 1L, start, end, "Too many days"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Insufficient balance");
    }

    // ---------------------------------------------------- cancel & delete

    @Test
    @Order(30)
    void cancelPendingThenRulesHold() {
        LocalDate start = nextMonday(LocalDate.now().plusWeeks(6));
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(),
                new LeaveDto.CreateLeaveRequest(6L, 2L, start, start.plusDays(1), "Will cancel"));
        Long id = extractId(created.getBody());
        assertThat(exchange(HttpMethod.PATCH, "/api/v1/leave/requests/" + id + "/cancel", hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // Cancelling a decided request is a 409
        assertThat(exchange(HttpMethod.PATCH, "/api/v1/leave/requests/" + id + "/cancel", hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(31)
    void deleteRulesPendingProtected() {
        // Seed request 1 is PENDING → cannot be hard-deleted
        ResponseEntity<String> pendingDelete = exchange(HttpMethod.DELETE, "/api/v1/leave/requests/1",
                hrHeaders(), null);
        assertThat(pendingDelete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pendingDelete.getBody()).contains("Cancel the pending request instead");

        // The rejected request from order 22 can be deleted — need its id: newest REJECTED
        ResponseEntity<String> list = exchange(HttpMethod.GET, "/api/v1/leave/requests?status=REJECTED",
                hrHeaders(), null);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        // list is ordered by id desc → first id is the newest (order 22's request)
        int idx = list.getBody().indexOf("\"id\":");
        long newestRejected = Long.parseLong(list.getBody().substring(idx + 5,
                list.getBody().indexOf(',', idx)));
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/leave/requests/" + newestRejected, hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // -------------------------------------------------- balances & summary

    @Test
    @Order(40)
    void balancesCountOnlyApprovedDays() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/leave/balances/5",
                managerHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();

        // Employee 5: seed APPROVED CASUAL 2 days (start d-20, same year in test runs)
        assertThat(body).contains("\"year\":" + LocalDate.now().getYear());
        assertThat(body).contains("\"usedDays\":2.0,\"remainingDays\":10.0");   // CASUAL 12
        assertThat(body).contains("\"usedDays\":0.0,\"remainingDays\":10.0");   // SICK 10
        assertThat(body).contains("\"usedDays\":0.0,\"remainingDays\":15.0");   // EARNED 15
    }

    @Test
    @Order(41)
    void summaryCounts() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/leave/summary", hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();

        // Still PENDING: seed ids 1 & 2 (mine were approved/rejected/cancelled)
        assertThat(body).contains("\"pendingCount\":2");
        // APPROVED this year: seed (emp 5) + order 20 → 2; REJECTED: seed only
        // (order 22's rejection is deleted in order 31) → decided 3 → rate 2/3 = 66.7%
        assertThat(body).contains("\"approvedThisYear\":2");
        assertThat(body).contains("\"approvalRate\":66.7");
    }

    // ----------------------------------------------------------------- RBAC

    @Test
    @Order(50)
    void employeeForbiddenManagerReadsButCannotWrite() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/leave/requests", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.GET, "/api/v1/leave/summary", managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        LocalDate start = nextMonday(LocalDate.now().plusWeeks(7));
        assertThat(exchange(HttpMethod.POST, "/api/v1/leave/requests", managerHeaders(),
                new LeaveDto.CreateLeaveRequest(3L, 1L, start, start.plusDays(1), "nope"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -------------------------------------------------------------- helpers

    /** First Monday strictly after the given date. */
    private static LocalDate nextMonday(LocalDate date) {
        LocalDate d = date;
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private static Long extractId(String body) {
        int idx = body.indexOf("\"id\":");
        return Long.parseLong(body.substring(idx + 5, body.indexOf(',', idx)));
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
