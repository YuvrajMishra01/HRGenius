package com.hrgenius.payroll;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Payroll queries: period views, run guards, totals, runs overview. */
public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    /** Payroll aggregates for one period: [count, sum(netSalary)]. */
    @Query("""
            select count(p), coalesce(sum(p.netSalary), 0)
            from Payroll p
            where p.payYear = :year and p.payMonth = :month
            """)
    List<Object[]> totalsForPeriod(@Param("year") int year, @Param("month") int month);

    /** Employees already in a period (run skip-set). */
    @Query("""
            select p.employee.id
            from Payroll p
            where p.payYear = :year and p.payMonth = :month
            """)
    Set<Long> findEmployeeIdsForPeriod(@Param("year") int year, @Param("month") int month);

    /** One employee's latest payslip (run baseline prefill). */
    java.util.Optional<Payroll> findFirstByEmployee_IdOrderByPayYearDescPayMonthDesc(Long employeeId);

    /** Period rows with employee + department resolved, code order. */
    @Query("""
            select p from Payroll p
            join fetch p.employee e
            left join fetch e.department d
            where p.payYear = :year and p.payMonth = :month
            order by e.employeeCode
            """)
    List<Payroll> findForPeriodWithEmployee(@Param("year") int year, @Param("month") int month);

    /** Runs overview: [year, month, count, sum(net)] newest first. */
    @Query("""
            select p.payYear, p.payMonth, count(p), coalesce(sum(p.netSalary), 0)
            from Payroll p
            group by p.payYear, p.payMonth
            order by p.payYear desc, p.payMonth desc
            """)
    List<Object[]> periodTotals();
}
