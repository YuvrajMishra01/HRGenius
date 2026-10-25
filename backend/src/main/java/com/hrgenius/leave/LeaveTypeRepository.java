package com.hrgenius.leave;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Leave type queries: uniqueness enforcement, dashboard KPI. */
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<LeaveType> findByNameIgnoreCase(String name);
}
