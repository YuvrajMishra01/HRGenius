package com.hrgenius.department;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrgenius.common.Lists;
import com.hrgenius.common.PageResponse;
import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department management (Phase 4). Deletes are blocked while employees are
 * assigned — an organization's history must not be orphaned.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final EmployeeRepository employeeRepository;

    /** Unpaged list — feeds dialogs and read-only tables (stable contract). */
    @Transactional(readOnly = true)
    public List<DepartmentDto.DepartmentResponse> list() {
        Map<Long, Long> counts = employeeCounts();
        return departmentRepository.findAllOrderedByName().stream()
                .map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    /** Admin table view: server-side search + pagination. */
    @Transactional(readOnly = true)
    public PageResponse<DepartmentDto.DepartmentResponse> listPaged(String search, Integer page, Integer size) {
        String term = Lists.cleanSearch(search);
        Map<Long, Long> counts = employeeCounts();
        List<DepartmentDto.DepartmentResponse> rows = departmentRepository.findAllOrderedByName().stream()
                .map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L)))
                .filter(d -> term == null
                        || d.name().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))
                .toList();
        return Lists.page(rows, Lists.cleanPage(page), Lists.cleanSize(size));
    }

    @Transactional(readOnly = true)
    public DepartmentDto.DepartmentResponse get(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
        return toResponse(department, departmentRepository.countEmployeesIn(id));
    }

    @Transactional
    public DepartmentDto.DepartmentResponse create(DepartmentDto.DepartmentRequest request) {
        departmentRepository.findByNameIgnoreCase(request.name().trim()).ifPresent(d -> {
            throw new IllegalStateException("Department already exists: " + request.name());
        });
        Department department = new Department();
        applyRequest(department, request);
        Department saved = departmentRepository.save(department);
        log.info("Department created: {}", saved.getName());
        return toResponse(saved, 0);
    }

    @Transactional
    public DepartmentDto.DepartmentResponse update(Long id, DepartmentDto.DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw new IllegalStateException("Department already exists: " + request.name());
        }
        applyRequest(department, request);
        Department saved = departmentRepository.save(department);
        log.info("Department updated: {}", saved.getName());
        return toResponse(saved, departmentRepository.countEmployeesIn(id));
    }

    /**
     * Hard delete, but guarded: only empty departments (no employees, no
     * designations) may be removed. This keeps referential history intact
     * while still allowing genuine cleanup.
     */
    @Transactional
    public void delete(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));

        long employees = departmentRepository.countEmployeesIn(id);
        if (employees > 0) {
            throw new IllegalStateException(
                    "Department still has " + employees + " employee(s). Reassign them first.");
        }
        if (designationRepository.findByDepartmentIdOrderByTitleAsc(id).size() > 0) {
            throw new IllegalStateException(
                    "Department still has designations. Move or delete them first.");
        }

        departmentRepository.delete(department);
        log.info("Department deleted: {}", department.getName());
    }

    // ------------------------------------------------------------ helpers

    private void applyRequest(Department department, DepartmentDto.DepartmentRequest request) {
        department.setName(request.name().trim());
        department.setDescription(request.description());
        department.setManager(resolveManager(request.managerId()));
    }

    private Employee resolveManager(Long managerId) {
        if (managerId == null) {
            return null;
        }
        return employeeRepository.findById(managerId)
                .orElseThrow(() -> new EntityNotFoundException("Manager not found: " + managerId));
    }

    private Map<Long, Long> employeeCounts() {
        Map<Long, Long> counts = new HashMap<>();
        departmentRepository.countEmployeesByDepartmentRaw()
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    private DepartmentDto.DepartmentResponse toResponse(Department d, long employeeCount) {
        return new DepartmentDto.DepartmentResponse(
                d.getId(),
                d.getName(),
                d.getDescription(),
                d.getManager() != null ? d.getManager().getId() : null,
                d.getManager() != null
                        ? d.getManager().getFirstName() + " " + d.getManager().getLastName()
                        : null,
                employeeCount);
    }
}
