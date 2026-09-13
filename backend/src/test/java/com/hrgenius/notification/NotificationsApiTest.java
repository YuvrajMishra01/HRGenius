package com.hrgenius.notification;

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

import com.hrgenius.auth.LoginRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-app notifications over real HTTP (Phase 12).
 *
 * Seed: admin (user 1) has one unread SYSTEM row; users 2–4 have none.
 * Events are delivered to USERS via the EMPLOYEES.EMAIL link — employee 2
 * (Anita Desai) matches the EMPLOYEE login, employee 4 (Sneha Patil) has no
 * login and silently receives nothing.
 *
 * Suite order is alphabetical: leave < notification < onboarding. We create
 * a leave request for employee 4 far in the future (no overlap with seeds or
 * the LeaveApiTest rows, all within ±40 days) so onboarding's employee
 * pickers are unaffected; requests are metadata-only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationsApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static volatile Long leaveRequestId;

    // ------------------------------------------------------- seed + scoping

    @Test
    @Order(1)
    void seedRowAndUnreadCountAreScopedToThePrincipal() {
        // Admin sees the seeded welcome notification.
        ResponseEntity<String> adminList = exchange(HttpMethod.GET, "/api/v1/notifications", adminHeaders(), null);
        assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminList.getBody()).contains("Welcome to HRGenius");

        ResponseEntity<String> adminUnread = exchange(HttpMethod.GET, "/api/v1/notifications/unread",
                adminHeaders(), null);
        assertThat(adminUnread.getBody()).contains("\"unread\":1");

        // HR starts empty — rows never leak across users.
        ResponseEntity<String> hrList = exchange(HttpMethod.GET, "/api/v1/notifications", hrHeaders(), null);
        assertThat(hrList.getBody()).contains("\"data\":[]");
        ResponseEntity<String> hrUnread = exchange(HttpMethod.GET, "/api/v1/notifications/unread",
                hrHeaders(), null);
        assertThat(hrUnread.getBody()).contains("\"unread\":0");
    }

    // ------------------------------------------------------- mark lifecycle

    @Test
    @Order(2)
    void markReadClearsTheBadgeAndMarksAllSweepsTheRest() {
        Long firstId = firstNotificationId(adminHeaders());
        ResponseEntity<String> marked = exchange(HttpMethod.PATCH, "/api/v1/notifications/" + firstId + "/read",
                adminHeaders(), null);
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(marked.getBody()).contains("\"read\":true");
        assertThat(unreadCount(adminHeaders())).isZero();

        // Idempotent: marking an already-read row stays 200 and stays read.
        ResponseEntity<String> again = exchange(HttpMethod.PATCH, "/api/v1/notifications/" + firstId + "/read",
                adminHeaders(), null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody()).contains("\"read\":true");

        // Re-arm via events, then read-all sweeps everything. Counts are
        // delta-based: LeaveApiTest's decisions also deliver LEAVE events
        // to this user (the badge is cumulative, never reset by suites).
        long before = unreadCount(employeeHeaders());
        submitLeaveForEmployee2();
        approveIt();
        assertThat(unreadCount(employeeHeaders())).isEqualTo(before + 2);

        ResponseEntity<String> all = exchange(HttpMethod.PATCH, "/api/v1/notifications/read-all",
                employeeHeaders(), null);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).contains("\"unread\":");
        assertThat(unreadCount(employeeHeaders())).isZero();
    }

    @Test
    @Order(3)
    void anotherUsersRowIsInvisibleAndUnknownIdIs404() {
        Long employeeOwnedId = firstNotificationId(employeeHeaders());
        assertThat(employeeOwnedId).isNotNull();

        // HR must get 404 (not 403): the row does not exist in her scope.
        ResponseEntity<String> hijack = exchange(HttpMethod.PATCH,
                "/api/v1/notifications/" + employeeOwnedId + "/read", hrHeaders(), null);
        assertThat(hijack.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> unknown = exchange(HttpMethod.PATCH,
                "/api/v1/notifications/999999/read", adminHeaders(), null);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------- event delivery

    @Test
    @Order(10)
    void leaveDecisionDeliversTwoNotificationsToTheLinkedEmployee() {
        // submitLeaveForEmployee2() + approveIt() ran in order 2; verify here.
        ResponseEntity<String> list = exchange(HttpMethod.GET, "/api/v1/notifications", employeeHeaders(), null);
        String body = list.getBody();
        assertThat(body).contains("Leave request submitted");
        assertThat(body).contains("Leave request approved");
        assertThat(body).contains("\"type\":\"LEAVE\"");
        // Both rows were already cleared by the read-all in order 2.
        assertThat(body).contains("\"read\":true");

        ResponseEntity<String> unread = exchange(HttpMethod.GET, "/api/v1/notifications/unread",
                employeeHeaders(), null);
        assertThat(unread.getBody()).contains("\"unread\":0");
    }

    @Test
    @Order(11)
    void employeeWithoutLoginReceivesNothing() {
        // Employee 4 (Sneha Patil) has no USERS row (her email is not a login).
        submitLeaveForEmployee4();
        ResponseEntity<String> adminUnread = exchange(HttpMethod.GET, "/api/v1/notifications/unread",
                adminHeaders(), null);
        // The event is dropped silently — no user to address.
        assertThat(adminUnread.getBody()).contains("\"unread\":0");
    }

    // ----------------------------------------------------------------- RBAC

    @Test
    @Order(50)
    void allRolesIncludingEmployeeCanManageTheirOwnFeed() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/notifications", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, "/api/v1/notifications", managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, "/api/v1/notifications", hrHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.PATCH, "/api/v1/notifications/read-all", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, "/api/v1/notifications", anonymousHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------- helpers

    /** Leave for Anita (employee 2, linked to the EMPLOYEE login): far future. */
    private void submitLeaveForEmployee2() {
        String start = LocalDate.now().plusYears(1).withMonth(3).withDayOfMonth(2).toString();
        String end = LocalDate.now().plusYears(1).withMonth(3).withDayOfMonth(3).toString();
        leaveRequestId = createLeave(2L, start, end);
        assertThat(leaveRequestId).isNotNull();
    }

    /** Leave for Sneha (employee 4, no linked login): far future, other type. */
    private void submitLeaveForEmployee4() {
        createLeave(4L, LocalDate.now().plusYears(1).withMonth(6).withDayOfMonth(10).toString(),
                LocalDate.now().plusYears(1).withMonth(6).withDayOfMonth(11).toString());
    }

    private Long createLeave(Long employeeId, String start, String end) {
        String body = "{\"employeeId\":" + employeeId + ",\"leaveTypeId\":3,\"startDate\":\"" + start
                + "\",\"endDate\":\"" + end + "\",\"reason\":\"Phase 12 notification event\"}";
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/leave/requests", hrHeaders(), body);
        assertThat(created.getStatusCode()).as("leave create for employee %s", employeeId)
                .isEqualTo(HttpStatus.CREATED);
        int idx = created.getBody().indexOf("\"id\":");
        return Long.parseLong(created.getBody().substring(idx + 5,
                created.getBody().indexOf(',', idx)));
    }

    private void approveIt() {
        ResponseEntity<String> approved = exchange(HttpMethod.PATCH,
                "/api/v1/leave/requests/" + leaveRequestId + "/approve", hrHeaders(), null);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Long firstNotificationId(HttpHeaders headers) {
        ResponseEntity<String> list = exchange(HttpMethod.GET, "/api/v1/notifications", headers, null);
        String body = list.getBody();
        int idx = body.indexOf("\"id\":");
        return idx < 0 ? null : Long.parseLong(body.substring(idx + 5, body.indexOf(',', idx)));
    }

    private long unreadCount(HttpHeaders headers) {
        ResponseEntity<String> unread = exchange(HttpMethod.GET, "/api/v1/notifications/unread", headers, null);
        String body = unread.getBody();
        int idx = body.indexOf("\"unread\":");
        return Long.parseLong(body.substring(idx + 9, body.indexOf('}', idx)));
    }

    private String url() {
        return "http://localhost:" + port;
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

    private HttpHeaders anonymousHeaders() {
        return new HttpHeaders();
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
}
