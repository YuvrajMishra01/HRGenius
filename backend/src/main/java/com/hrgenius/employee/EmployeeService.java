package com.hrgenius.employee;

import java.util.List;

import com.hrgenius.common.PageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrgenius.department.Department;
import com.hrgenius.department.DepartmentRepository;
import com.hrgenius.department.Designation;
import com.hrgenius.department.DesignationRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Employee business logic (Phase 3). Enforces uniqueness rules, resolves
 * FK references, and implements DELETE as a soft delete (status →
 * TERMINATED) because attendance/payroll/manager history must survive.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    /** Sortable columns whitelist — never pass client input to Sort directly. */
    private static final List<String> SORTABLE = List.of(
            "employeeCode", "firstName", "lastName", "email", "joiningDate", "status", "createdAt");

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDto.Response> list(
            String search, Long departmentId, EmployeeStatus status, EmploymentType type,
            int page, int size, String sortBy, String sortDir) {

        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), sort);

        Specification<Employee> spec = Specification.where(null);
        if (search != null && !search.isBlank()) {
            spec = spec.and(EmployeeSpecifications.search(search.trim()));
        }
        spec = spec.and(EmployeeSpecifications.inDepartment(departmentId));
        spec = spec.and(EmployeeSpecifications.withStatus(status));
        spec = spec.and(EmployeeSpecifications.withType(type));

        Page<EmployeeDto.Response> result = employeeRepository.findAll(spec, pageable)
                .map(EmployeeService::toResponse);
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public EmployeeDto.Response get(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + id));
        return toResponse(employee);
    }

    @Transactional
    public EmployeeDto.Response create(EmployeeDto request) {
        requireUniqueCode(request.employeeCode(), null);
        requireUniqueEmail(request.email(), null);

        Employee employee = new Employee();
        applyRequest(employee, request);
        Employee saved = employeeRepository.save(employee);
        log.info("Employee created: [{}] {}", saved.getEmployeeCode(), saved.getEmail());
        return toResponse(saved);
    }

    @Transactional
    public EmployeeDto.Response update(Long id, EmployeeDto request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + id));

        requireUniqueCode(request.employeeCode(), id);
        requireUniqueEmail(request.email(), id);
        if (id.equals(request.managerId())) {
            throw new IllegalStateException("An employee cannot be their own manager");
        }

        applyRequest(employee, request);
        Employee saved = employeeRepository.save(employee);
        log.info("Employee updated: [{}] {}", saved.getEmployeeCode(), saved.getEmail());
        return toResponse(saved);
    }

    /**
     * Soft delete: keep the row and its history, mark TERMINATED.
     * Blocks deletion while the employee still manages other employees.
     */
    @Transactional
    public void delete(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + id));

        if (employeeRepository.existsByManagerId(id)) {
            throw new IllegalStateException(
                    "Reassign managed employees before removing this employee");
        }

        employee.setStatus(EmployeeStatus.TERMINATED);
        employeeRepository.save(employee);
        log.info("Employee soft-deleted (TERMINATED): [{}]", employee.getEmployeeCode());
    }

    // ------------------------------------------------------------ helpers

    private void applyRequest(Employee employee, EmployeeDto request) {
        employee.setEmployeeCode(request.employeeCode());
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setGender(request.gender());
        employee.setAddress(request.address());
        employee.setJoiningDate(request.joiningDate());
        employee.setEmploymentType(request.employmentType());
        employee.setStatus(request.status());
        employee.setDepartment(resolveDepartment(request.departmentId()));
        employee.setDesignation(resolveDesignation(request.designationId(), request.departmentId()));
        employee.setManager(resolveManager(request.managerId()));
    }

    private void requireUniqueCode(String code, Long excludeId) {
        boolean taken = excludeId == null
                ? employeeRepository.existsByEmployeeCodeIgnoreCase(code)
                : employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot(code, excludeId);
        if (taken) {
            throw new IllegalStateException("Employee code already exists: " + code);
        }
    }

    private void requireUniqueEmail(String email, Long excludeId) {
        boolean taken = excludeId == null
                ? employeeRepository.existsByEmailIgnoreCase(email)
                : employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, excludeId);
        if (taken) {
            throw new IllegalStateException("Email already exists: " + email);
        }
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + departmentId));
    }

    private Designation resolveDesignation(Long designationId, Long departmentId) {
        if (designationId == null) {
            return null;
        }
        Designation designation = designationRepository.findById(designationId)
                .orElseThrow(() -> new EntityNotFoundException("Designation not found: " + designationId));
        // Keep designation and department consistent.
        if (departmentId != null && designation.getDepartment() != null
                && !designation.getDepartment().getId().equals(departmentId)) {
            throw new IllegalArgumentException(
                    "Designation " + designation.getTitle() + " does not belong to the selected department");
        }
        return designation;
    }

    private Employee resolveManager(Long managerId) {
        if (managerId == null) {
            return null;
        }
        return employeeRepository.findById(managerId)
                .orElseThrow(() -> new EntityNotFoundException("Manager not found: " + managerId));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String property = SORTABLE.contains(sortBy) ? sortBy : "employeeCode";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    static EmployeeDto.Response toResponse(Employee e) {
        return new EmployeeDto.Response(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName(),
                e.getLastName(),
                e.getEmail(),
                e.getPhone(),
                e.getDateOfBirth(),
                e.getGender(),
                e.getAddress(),
                e.getJoiningDate(),
                e.getEmploymentType(),
                e.getStatus(),
                e.getDepartment() != null ? e.getDepartment().getId() : null,
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getDesignation() != null ? e.getDesignation().getId() : null,
                e.getDesignation() != null ? e.getDesignation().getTitle() : null,
                e.getManager() != null ? e.getManager().getId() : null,
                e.getManager() != null
                        ? e.getManager().getFirstName() + " " + e.getManager().getLastName()
                        : null);
    }
}
