package com.hrgenius.recruitment;

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

/** Scheduled interview for an application. Mapped to INTERVIEWS (Flyway V1). */
@Entity
@Table(name = "INTERVIEWS")
@Getter
@Setter
public class Interview {

    public enum InterviewMode {
        ONSITE, ONLINE, PHONE
    }

    public enum InterviewStatus {
        SCHEDULED, COMPLETED, CANCELLED
    }

    public enum InterviewResult {
        PASS, FAIL, ON_HOLD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "APPLICATION_ID", nullable = false)
    private JobApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "INTERVIEWER_ID")
    private Employee interviewer;

    @Column(name = "INTERVIEW_DATE", nullable = false)
    private OffsetDateTime interviewDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "INTERVIEW_MODE", nullable = false, length = 20)
    private InterviewMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Column(name = "FEEDBACK", length = 1000)
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "RESULT", length = 20)
    private InterviewResult result;
}
