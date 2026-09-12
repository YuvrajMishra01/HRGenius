package com.hrgenius.employee;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for create/update. IDs of related records (department,
 * designation, manager) are supplied as nullable Longs and resolved in
 * the service layer — entities never enter or leave controllers.
 */
public record EmployeeDto(
        @NotBlank(message = "Employee code is required")
        @Size(max = 20, message = "Employee code must be at most 20 characters")
        @Pattern(regexp = "[A-Za-z0-9\\-]+", message = "Employee code may contain letters, digits and dashes only")
        String employeeCode,

        @NotBlank(message = "First name is required")
        @Size(max = 60)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 60)
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 150)
        String email,

        @Size(max = 20)
        String phone,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        Gender gender,

        @Size(max = 255)
        String address,

        @NotNull(message = "Joining date is required")
        LocalDate joiningDate,

        @NotNull(message = "Employment type is required")
        EmploymentType employmentType,

        @NotNull(message = "Status is required")
        EmployeeStatus status,

        Long departmentId,

        Long designationId,

        Long managerId
) {

    /** Full projection returned by the API. */
    public record Response(
            Long id,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            Gender gender,
            String address,
            LocalDate joiningDate,
            EmploymentType employmentType,
            EmployeeStatus status,
            Long departmentId,
            String departmentName,
            Long designationId,
            String designationTitle,
            Long managerId,
            String managerName
    ) {}
}
