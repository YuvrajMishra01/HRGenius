package com.hrgenius.department;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    @Query("select d from Department d order by d.name")
    List<Department> findAllOrderedByName();

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** [departmentId, employeeCount] for every department that has employees. */
    @Query("select e.department.id, count(e) from Employee e where e.department is not null group by e.department.id")
    List<Object[]> countEmployeesByDepartmentRaw();

    /** Total employees in one department (delete guard + response detail). */
    @Query("select count(e) from Employee e where e.department.id = :departmentId")
    long countEmployeesIn(@Param("departmentId") Long departmentId);
}
