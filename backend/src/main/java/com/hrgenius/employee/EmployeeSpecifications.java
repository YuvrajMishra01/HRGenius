package com.hrgenius.employee;

import org.springframework.data.jpa.domain.Specification;

/**
 * Composable filters for the employee list. Each spec returns null-safe
 * conjunctions — the service combines only the filters actually supplied.
 */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {}

    /** Case-insensitive contains across name, code and email. */
    public static Specification<Employee> search(String term) {
        String like = "%" + term.toLowerCase() + "%";
        // Explicit escape char: without it Hibernate 6 renders `like ? escape ''`,
        // which H2 treats as literal matching (no wildcard expansion).
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), like, '\\'),
                cb.like(cb.lower(root.get("lastName")), like, '\\'),
                cb.like(cb.lower(root.get("employeeCode")), like, '\\'),
                cb.like(cb.lower(root.get("email")), like, '\\'));
    }

    public static Specification<Employee> inDepartment(Long departmentId) {
        return (root, query, cb) ->
                departmentId == null ? null : cb.equal(root.get("department").get("id"), departmentId);
    }

    public static Specification<Employee> withStatus(EmployeeStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Employee> withType(EmploymentType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("employmentType"), type);
    }
}
