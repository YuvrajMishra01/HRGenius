package com.hrgenius.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.hrgenius.employee.Employee;

import lombok.Getter;
import lombok.Setter;

/** Daily attendance record. Mapped to ATTENDANCE (Flyway V1). */
@Entity
@Table(name = "ATTENDANCE")
@Getter
@Setter
public class Attendance {

    public enum AttendanceStatus {
        PRESENT, ABSENT, HALF_DAY, LEAVE, HOLIDAY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EMPLOYEE_ID", nullable = false)
    private Employee employee;

    @Column(name = "ATTENDANCE_DATE", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "CHECK_IN")
    private OffsetDateTime checkIn;

    @Column(name = "CHECK_OUT")
    private OffsetDateTime checkOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "WORKING_HOURS", precision = 4, scale = 2)
    private BigDecimal workingHours;
}
