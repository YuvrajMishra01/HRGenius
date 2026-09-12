package com.hrgenius.department;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DesignationRepository extends JpaRepository<Designation, Long> {

    List<Designation> findByDepartmentIdOrderByTitleAsc(Long departmentId);

    List<Designation> findAllByOrderByTitleAsc();

    Optional<Designation> findByTitleIgnoreCaseAndDepartmentId(String title, Long departmentId);

    boolean existsByTitleIgnoreCaseAndDepartmentIdAndIdNot(String title, Long departmentId, Long id);

    /** [designationId, employeeCount] for every designation in use. */
    @Query("select e.designation.id, count(e) from Employee e where e.designation is not null group by e.designation.id")
    List<Object[]> countEmployeesByDesignationRaw();

    /** Employees holding one designation (delete guard + response detail). */
    @Query("select count(e) from Employee e where e.designation.id = :designationId")
    long countEmployeesWith(@Param("designationId") Long designationId);
}
