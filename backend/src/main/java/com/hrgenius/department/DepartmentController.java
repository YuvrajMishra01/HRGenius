package com.hrgenius.department;

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
 * Department & designation management (Phase 4).
 *
 * RBAC: everyone with directory access (ADMIN, HR, MANAGER) can read;
 * mutations are ADMIN-only — they shape the org structure itself.
 */
@RestController
@RequestMapping("/api/v1")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DesignationService designationService;

    public DepartmentController(DepartmentService departmentService,
                                DesignationService designationService) {
        this.departmentService = departmentService;
        this.designationService = designationService;
    }

    // -------------------------------------------------------- departments

    /** Unpaged list — feeds dialogs and read-only tables (stable contract). */
    @GetMapping("/departments")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<DepartmentDto.DepartmentResponse>>> departments() {
        return ResponseEntity.ok(ApiResponse.of(departmentService.list()));
    }

    /** Admin table view with server-side search + pagination. */
    @GetMapping("/departments/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<DepartmentDto.DepartmentResponse>>> departmentsPaged(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(departmentService.listPaged(search, page, size)));
    }

    @GetMapping("/departments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<DepartmentDto.DepartmentResponse>> department(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(departmentService.get(id)));
    }

    @PostMapping("/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentDto.DepartmentResponse>> createDepartment(
            @Valid @RequestBody DepartmentDto.DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Department created", departmentService.create(request)));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentDto.DepartmentResponse>> updateDepartment(
            @PathVariable Long id, @Valid @RequestBody DepartmentDto.DepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Department updated", departmentService.update(id, request)));
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id) {
        departmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Department deleted"));
    }

    // ------------------------------------------------------- designations

    /** Unpaged list — feeds dialogs and the designation-by-department view. */
    @GetMapping("/designations")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<DepartmentDto.DesignationResponse>>> designations(
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(ApiResponse.of(designationService.list(departmentId)));
    }

    /** Admin table view with server-side search + pagination. */
    @GetMapping("/designations/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<DepartmentDto.DesignationResponse>>> designationsPaged(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(designationService.listPaged(search, page, size)));
    }

    @PostMapping("/designations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentDto.DesignationResponse>> createDesignation(
            @Valid @RequestBody DepartmentDto.DesignationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Designation created", designationService.create(request)));
    }

    @PutMapping("/designations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentDto.DesignationResponse>> updateDesignation(
            @PathVariable Long id, @Valid @RequestBody DepartmentDto.DesignationRequest request) {
        return ResponseEntity.ok(ApiResponse.of("Designation updated", designationService.update(id, request)));
    }

    @DeleteMapping("/designations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDesignation(@PathVariable Long id) {
        designationService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Designation deleted"));
    }
}
