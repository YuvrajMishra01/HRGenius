package com.hrgenius.leave;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

/** Employee leave request and its approval state. Mapped to LEAVE_REQUESTS (V1). */
@Entity
@Table(name = "LEAVE_REQUESTS")
@Getter
@Setter
public class LeaveRequest {

    public enum LeaveStatus {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EMPLOYEE_ID", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LEAVE_TYPE_ID", nullable = false)
    private LeaveType leaveType;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    @Column(name = "REASON", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private LeaveStatus status = LeaveStatus.PENDING;

    /** Approving user (FK → USERS), set when approved/rejected. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "APPROVED_BY")
    private com.hrgenius.auth.User approvedBy;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
