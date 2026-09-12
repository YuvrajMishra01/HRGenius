package com.hrgenius.attendance;

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
 * Attendance over real HTTP against the seeded H2 database (Phase 7).
 *
 * Class order is alphabetical (junit-platform.properties): analytics →
 * attendance → auth → … — so the dashboard suite still sees pure seed state,
 * and suites running later (employee/onboarding) are unaffected because only
 * THIS suite reads attendance.
 *
 * Seed: employees 1–7 (no attendance today); employees 1–6 have records for
 * the previous 4 days. Divya = employee 6, Rahul = employee 1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttendanceApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // ------------------------------------------------- check-in / check-out

    @Test
    @Order(1)
    void checkInThenCheckOutCreatesPresentRecordWithHours() {
        ResponseEntity<String> in = exchange(HttpMethod.POST, "/api/v1/attendance/check-in?employeeId=6",
                hrHeaders(), null);
        assertThat(in.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = in.getBody();
        assertThat(body).contains("\"status\":\"PRESENT\"");
        assertThat(body).contains("\"checkIn\":");          // timestamp set
        assertThat(body).contains("Divya Nair");
        assertThat(body).doesNotContain("\"checkOut\"");    // non_null omits it

        ResponseEntity<String> out = exchange(HttpMethod.POST, "/api/v1/attendance/check-out?employeeId=6",
                hrHeaders(), null);
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
        String outBody = out.getBody();
        assertThat(outBody).contains("\"checkOut\":");
        assertThat(outBody).contains("\"workingHours\":");  // computed on check-out
    }

    @Test
    @Order(2)
    void secondCheckInIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/attendance/check-in?employeeId=6",
                hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Already checked in today");
    }

    @Test
    @Order(3)
    void secondCheckOutIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/attendance/check-out?employeeId=6",
                hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Already checked out today");
    }

    @Test
    @Order(4)
    void checkOutWithoutCheckInIs409() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/attendance/check-out?employeeId=2",
                hrHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("No check-in found for today");
    }

    @Test
    @Order(5)
    void unknownEmployeeIs404() {
        assertThat(exchange(HttpMethod.POST, "/api/v1/attendance/check-in?employeeId=9999",
                hrHeaders(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------------- manual marking

    @Test
    @Order(10)
    void markCreatesThenUpsertsSameDay() {
        LocalDate day = LocalDate.now().minusDays(5);
        // Vikram (3) has no record for day-5
        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/attendance/mark", hrHeaders(),
                new AttendanceDto.MarkRequest(3L, day, Attendance.AttendanceStatus.HALF_DAY));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).contains("\"status\":\"HALF_DAY\"");

        // Upsert: same employee+day flips the status (UK_ATT_EMP_DATE respected)
        ResponseEntity<String> updated = exchange(HttpMethod.POST, "/api/v1/attendance/mark", hrHeaders(),
                new AttendanceDto.MarkRequest(3L, day, Attendance.AttendanceStatus.PRESENT));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).contains("\"status\":\"PRESENT\"");
    }

    @Test
    @Order(11)
    void markingNonWorkingStatusClearsTimestamps() {
        // Anita (2) has no record today → LEAVE record without times
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/attendance/mark", hrHeaders(),
                new AttendanceDto.MarkRequest(2L, LocalDate.now(), Attendance.AttendanceStatus.LEAVE));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("\"status\":\"LEAVE\"");
        assertThat(body).doesNotContain("\"checkIn\"");   // cleared → omitted by non_null
        assertThat(body).doesNotContain("\"checkOut\"");
    }

    @Test
    @Order(12)
    void markWithMissingFieldsIs400() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/attendance/mark", hrHeaders(),
                "{\"employeeId\":3,\"date\":null,\"status\":null}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"errors\"");
    }

    // ---------------------------------------------------------------- views

    @Test
    @Order(20)
    void todayViewSummarizesLiveRecords() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/attendance/today",
                managerHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        // From orders 1 & 11: Divya PRESENT (checked in+out), Anita LEAVE
        assertThat(body).contains("\"present\":1").contains("\"leave\":1").contains("\"total\":2");
        assertThat(body).contains("Divya Nair").contains("Anita Desai");
    }

    @Test
    @Order(21)
    void monthViewRollsUpPerEmployee() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/attendance/month",
                adminHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();

        // At this point in the class sequence only the 7 seed employees exist.
        assertThat(body).contains("\"month\":9");
        assertThat(body).contains("Rohan Kulkarni");   // row exists (no records yet)
        assertThat(body).contains("Divya Nair");

        // Divya: seed PRESENT(d-1) PRESENT(d-2) LEAVE(d-3) + today PRESENT
        //   → present 3, leave 1, total 4 → (3 + 0.5·0) / 4 = 75.0%
        int today = LocalDate.now().getDayOfMonth();
        int yesterday = LocalDate.now().minusDays(1).getDayOfMonth();
        assertThat(body).contains("\"totalRecords\":4");
        assertThat(body).contains("\"attendancePercent\":75.0");
        assertThat(body).contains("\"" + today + "\":\"PRESENT\"");
        assertThat(body).contains("\"" + yesterday + "\":\"PRESENT\"");

        // Rahul: PRESENT(d-1) PRESENT(d-2) LEAVE(d-3) → 2/3 = 66.7%
        assertThat(body).contains("\"attendancePercent\":66.7");
    }

    // ----------------------------------------------------------------- RBAC

    @Test
    @Order(30)
    void employeeIsForbiddenManagerReadsButCannotWrite() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/attendance/today", employeeHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.GET, "/api/v1/attendance/month", managerHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(exchange(HttpMethod.POST, "/api/v1/attendance/check-in?employeeId=3",
                managerHeaders(), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // HR can mark (already exercised above) — admin path sanity:
        assertThat(exchange(HttpMethod.GET, "/api/v1/attendance/today", adminHeaders(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------- helpers

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
