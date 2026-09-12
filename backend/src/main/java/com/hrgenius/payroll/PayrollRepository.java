package com.hrgenius.payroll;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    /** Payroll aggregates for one period: [count, sum(netSalary)]. */
    @Query("""
            select count(p), coalesce(sum(p.netSalary), 0)
            from Payroll p
            where p.payYear = :year and p.payMonth = :month
            """)
    List<Object[]> totalsForPeriod(@Param("year") int year, @Param("month") int month);
}
