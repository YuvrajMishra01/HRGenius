package com.hrgenius.department;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrgenius.employee.EmployeeRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Designation management (Phase 4). Titles are unique per department;
 * deletion is blocked while employees hold the title.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesignationService {

    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<DepartmentDto.DesignationResponse> list(Long departmentId) {
        Map<Long, Long> counts = employeeCounts();
        List<Designation> designations = departmentId == null
                ? designationRepository.findAllByOrderByTitleAsc()
                : designationRepository.findByDepartmentIdOrderByTitleAsc(departmentId);
        return designations.stream()
                .map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    @Transactional
    public DepartmentDto.DesignationResponse create(DepartmentDto.DesignationRequest request) {
        Department department = resolveDepartment(request.departmentId());
        String title = request.title().trim();
        designationRepository.findByTitleIgnoreCaseAndDepartmentId(title, department.getId())
                .ifPresent(d -> {
                    throw new IllegalStateException("Title already exists in this department: " + title);
                });

        Designation designation = new Designation();
        designation.setTitle(title);
        designation.setDescription(request.description());
        designation.setDepartment(department);
        Designation saved = designationRepository.save(designation);
        log.info("Designation created: {} ({})", saved.getTitle(),
                department.getName());
        return toResponse(saved, 0);
    }

    @Transactional
    public DepartmentDto.DesignationResponse update(Long id, DepartmentDto.DesignationRequest request) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Designation not found: " + id));
        Department department = resolveDepartment(request.departmentId());
        String title = request.title().trim();

        if (designationRepository.existsByTitleIgnoreCaseAndDepartmentIdAndIdNot(
                title, department.getId(), id)) {
            throw new IllegalStateException("Title already exists in this department: " + title);
        }

        designation.setTitle(title);
        designation.setDescription(request.description());
        designation.setDepartment(department);
        Designation saved = designationRepository.save(designation);
        log.info("Designation updated: {} ({})", saved.getTitle(), department.getName());
        return toResponse(saved, designationRepository.countEmployeesWith(id));
    }

    @Transactional
    public void delete(Long id) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Designation not found: " + id));

        long holders = designationRepository.countEmployeesWith(id);
        if (holders > 0) {
            throw new IllegalStateException(
                    "Designation is held by " + holders + " employee(s). Reassign them first.");
        }
        designationRepository.delete(designation);
        log.info("Designation deleted: {}", designation.getTitle());
    }

    // ------------------------------------------------------------ helpers

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            throw new IllegalArgumentException("Designations must belong to a department");
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + departmentId));
    }

    private Map<Long, Long> employeeCounts() {
        Map<Long, Long> counts = new HashMap<>();
        designationRepository.countEmployeesByDesignationRaw()
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    private DepartmentDto.DesignationResponse toResponse(Designation d, long employeeCount) {
        return new DepartmentDto.DesignationResponse(
                d.getId(),
                d.getTitle(),
                d.getDescription(),
                d.getDepartment() != null ? d.getDepartment().getId() : null,
                d.getDepartment() != null ? d.getDepartment().getName() : null,
                employeeCount);
    }
}
