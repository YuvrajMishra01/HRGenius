package com.hrgenius.onboarding;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import com.hrgenius.recruitment.JobApplication;

import lombok.Getter;
import lombok.Setter;

/**
 * Onboarding record for a new hire. Mapped to ONBOARDINGS (Flyway V1, linked
 * to its recruitment origin by V4). CHECKLIST is a CLOB converted to a typed
 * list; COMPLETION_PERCENTAGE is derived from it on every write.
 */
@Entity
@Table(name = "ONBOARDINGS")
@Getter
@Setter
public class Onboarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EMPLOYEE_ID", nullable = false)
    private Employee employee;

    /** Recruitment origin; null for employees onboarded outside the pipeline. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "APPLICATION_ID")
    private JobApplication application;

    @Column(name = "JOINING_DATE")
    private LocalDate joiningDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private OnboardingStatus status = OnboardingStatus.PENDING;

    @Column(name = "COMPLETION_PERCENTAGE", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercentage = BigDecimal.ZERO;

    @Convert(converter = ChecklistConverter.class)
    @Column(name = "CHECKLIST")
    private java.util.List<ChecklistItem> checklist;
}
