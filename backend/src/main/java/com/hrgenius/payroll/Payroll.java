package com.hrgenius.payroll;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.hrgenius.employee.Employee;

import lombok.Getter;
import lombok.Setter;

/**
 * Monthly payslip. Mapped to PAYROLLS (Flyway V1). All money is
 * BigDecimal (never floating point) per the project's financial rules.
 */
@Entity
@Table(name = "PAYROLLS")
@Getter
@Setter
public class Payroll {

    public enum PayrollStatus {
        DRAFT, PROCESSED, PAID
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EMPLOYEE_ID", nullable = false)
    private Employee employee;

    @Column(name = "PAY_MONTH", nullable = false)
    private int payMonth;

    @Column(name = "PAY_YEAR", nullable = false)
    private int payYear;

    @Column(name = "BASIC_SALARY", nullable = false, precision = 12, scale = 2)
    private BigDecimal basicSalary;

    @Column(name = "ALLOWANCES", nullable = false, precision = 12, scale = 2)
    private BigDecimal allowances;

    @Column(name = "DEDUCTIONS", nullable = false, precision = 12, scale = 2)
    private BigDecimal deductions;

    @Column(name = "TAX", nullable = false, precision = 12, scale = 2)
    private BigDecimal tax;

    @Column(name = "NET_SALARY", nullable = false, precision = 12, scale = 2)
    private BigDecimal netSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private PayrollStatus status = PayrollStatus.DRAFT;
}
