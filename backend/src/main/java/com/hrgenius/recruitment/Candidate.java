package com.hrgenius.recruitment;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** Job applicant (not yet an employee). Mapped to CANDIDATES (Flyway V1). */
@Entity
@Table(name = "CANDIDATES")
@Getter
@Setter
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "EMAIL", nullable = false, length = 150)
    private String email;

    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "RESUME_PATH", length = 255)
    private String resumePath;

    /** Comma-separated skill tags (normalized storage arrives with the AI module). */
    @Column(name = "SKILLS", length = 500)
    private String skills;

    @Column(name = "EXPERIENCE_YEARS", precision = 4, scale = 1)
    private java.math.BigDecimal experienceYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CandidateStatus status = CandidateStatus.NEW;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
