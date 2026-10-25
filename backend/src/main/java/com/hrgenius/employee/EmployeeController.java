package com.hrgenius.employee;

import java.util.List;

import com.hrgenius.common.ApiResponse;
import com.hrgenius.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee CRUD (Phase 3).
 *
 * RBAC per API_DOCUMENTATION:
 *   list/get  — ADMIN, HR, MANAGER (managers see the full directory for now;
 *               team scoping arrives with the self-service phase)
 *   create    — ADMIN, HR
 *   update    — ADMIN, HR
 *   delete    — ADMIN only (soft delete)
 */
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;

    public EmployeeController(EmployeeService employeeService, EmployeeRepository employeeRepository) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeDto.Response>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "employeeCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        return ResponseEntity.ok(ApiResponse.of(
                employeeService.list(search, departmentId, status, employmentType,
                        page, size, sortBy, sortDir)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<EmployeeDto.Response>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(employeeService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<EmployeeDto.Response>> create(@Valid @RequestBody EmployeeDto request) {
        EmployeeDto.Response created = employeeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of("Employee created", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<EmployeeDto.Response>> update(
            @PathVariable Long id, @Valid @RequestBody EmployeeDto request) {
        return ResponseEntity.ok(ApiResponse.of("Employee updated", employeeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Employee deactivated (status TERMINATED)"));
    }

    /** Lightweight id+name list for manager pickers and assignment UIs. */
    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<EmployeeOption>>> options() {
        List<EmployeeOption> options = employeeRepository.findAllByOrderByFirstNameAsc().stream()
                .map(e -> new EmployeeOption(e.getId(),
                        e.getFirstName() + " " + e.getLastName() + " (" + e.getEmployeeCode() + ")"))
                .toList();
        return ResponseEntity.ok(ApiResponse.of(options));
    }

    public record EmployeeOption(Long id, String label) {}
}
