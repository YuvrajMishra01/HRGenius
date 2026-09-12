package com.hrgenius.department;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs for department & designation management (Phase 4). */
public final class DepartmentDto {

    private DepartmentDto() {}

    public record DepartmentRequest(
            @NotBlank(message = "Department name is required")
            @Size(max = 100, message = "Name must be at most 100 characters")
            String name,

            @Size(max = 255)
            String description,

            Long managerId
    ) {}

    /** List/detail projection with the live employee count. */
    public record DepartmentResponse(
            Long id,
            String name,
            String description,
            Long managerId,
            String managerName,
            long employeeCount
    ) {}

    public record DesignationRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 100, message = "Title must be at most 100 characters")
            String title,

            @Size(max = 255)
            String description,

            Long departmentId
    ) {}

    public record DesignationResponse(
            Long id,
            String title,
            String description,
            Long departmentId,
            String departmentName,
            long employeeCount
    ) {}

    /** Convenience bundle for the management page: one round trip. */
    public record ManagementPage(
            List<DepartmentResponse> departments,
            List<DesignationResponse> designations
    ) {}
}
