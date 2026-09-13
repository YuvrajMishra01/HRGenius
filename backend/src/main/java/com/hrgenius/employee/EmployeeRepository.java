package com.hrgenius.employee;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Employee queries. Aggregations are computed in the database (GROUP BY),
 * not in Java — the dashboard stays fast as data grows. JpaSpecificationExecutor
 * powers the Phase 3 dynamic search/filter/sort/pagination list.
 */
public interface EmployeeRepository
        extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    long countByStatus(EmployeeStatus status);

    /** Hires within the last N days (KPI card). */
    long countByJoiningDateGreaterThanEqual(LocalDate since);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmployeeCodeIgnoreCaseAndIdNot(String employeeCode, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByManagerId(Long managerId);

    /** All employees ordered by first name (manager pickers, assignment UIs). */
    List<Employee> findAllByOrderByFirstNameAsc();

    // ---------------------------------------------- dashboard analytics

    /** Employees per department name (all statuses): [departmentName, count]. */
    @Query("""
            select d.name, count(e)
            from Employee e join e.department d
            group by d.name
            """)
    List<Object[]> countByDepartmentRaw();

    /**
     * Employees hired per month since the given date:
     * [year, month, count] ordered chronologically. HQL year()/month() work
     * on both Oracle and H2.
     */
    @Query("""
            select year(e.joiningDate), month(e.joiningDate), count(e)
            from Employee e
            where e.joiningDate >= :since
            group by year(e.joiningDate), month(e.joiningDate)
            order by year(e.joiningDate), month(e.joiningDate)
            """)
    List<Object[]> countHiresByMonthSince(@Param("since") LocalDate since);

    /** New hires joined within the last N days (dashboard "recent hires"). */
    List<Employee> findByJoiningDateGreaterThanEqualOrderByJoiningDateDesc(LocalDate since, Pageable pageable);

    Page<Employee> findByStatus(EmployeeStatus status, Pageable pageable);

    // ---------------------------------------------- analytics (Phase 13)

    /** Employees per employment type (all statuses): [type, count]. */
    @Query("""
            select e.employmentType, count(e)
            from Employee e
            group by e.employmentType
            """)
    List<Object[]> countByEmploymentTypeRaw();

    /** Every joining date (avg tenure + cohort buckets are computed in Java). */
    @Query("select e.joiningDate from Employee e")
    List<LocalDate> findAllJoiningDates();
}
