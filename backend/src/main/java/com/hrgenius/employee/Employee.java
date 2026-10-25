package com.hrgenius.employee;

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

import com.hrgenius.department.Department;
import com.hrgenius.department.Designation;

import lombok.Getter;
import lombok.Setter;

/**
 * Employee record. Maps to EMPLOYEES (Flyway V1). FK columns are lazy
 * ManyToOne associations so list queries stay cheap (avoid N+1 in DTO
 * mapping by fetching associations explicitly where needed).
 */
@Entity
@Table(name = "EMPLOYEES")
@Getter
@Setter
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLOYEE_CODE", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @Column(name = "FIRST_NAME", nullable = false, length = 60)
    private String firstName;

    @Column(name = "LAST_NAME", nullable = false, length = 60)
    private String lastName;

    @Column(name = "EMAIL", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "DATE_OF_BIRTH")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "GENDER", length = 10)
    private Gender gender;

    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "JOINING_DATE", nullable = false)
    private LocalDate joiningDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "EMPLOYMENT_TYPE", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private EmployeeStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DEPARTMENT_ID")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DESIGNATION_ID")
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MANAGER_ID")
    private Employee manager;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
