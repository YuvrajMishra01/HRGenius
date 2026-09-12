package com.hrgenius.recruitment;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.hrgenius.department.Department;
import com.hrgenius.employee.EmploymentType;

import lombok.Getter;
import lombok.Setter;

/** Job opening. Mapped to JOBS (Flyway V1). */
@Entity
@Table(name = "JOBS")
@Getter
@Setter
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TITLE", nullable = false, length = 150)
    private String title;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DEPARTMENT_ID")
    private Department department;

    @Column(name = "LOCATION", length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "EMPLOYMENT_TYPE", length = 20)
    private EmploymentType employmentType;

    @Column(name = "SALARY_RANGE", length = 60)
    private String salaryRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "POSTED_DATE")
    private LocalDate postedDate;

    @Column(name = "CLOSING_DATE")
    private LocalDate closingDate;
}
