package com.hrgenius.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;
import com.hrgenius.employee.EmployeeStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Payroll (Phase 9): generate the monthly run as DRAFT payslips for all
 * ACTIVE employees, adjust components, and drive the payslip lifecycle
 * DRAFT → PROCESSED → PAID. All money math is BigDecimal (never floating
 * point), per the project's financial rules.
 *
 * A run copies each employee's most recent payslip components as the draft
 * baseline; net salary is always recomputed, never trusted from input.
 *
 * Error contract: EntityNotFoundException → 404, IllegalArgumentException
 * → 400, IllegalStateException → 409 (via GlobalExceptionHandler).
 */
@Service
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;

    public PayrollService(PayrollRepository payrollRepository,
                          EmployeeRepository employeeRepository) {
        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
    }

    // ------------------------------------------------------------ run

    /**
     * Generate DRAFT payslips for the period. Every ACTIVE employee not yet
     * in the period gets one payslip, pre-filled from their latest existing
     * payslip (or zeros); existing rows are never touched (skipped).
     */
    @Transactional
    public PayrollDto.RunResponse run(int year, int month) {
        validatePeriod(year, month);

        Set<Long> existing = new HashSet<>(payrollRepository.findEmployeeIdsForPeriod(year, month));
        List<Employee> actives =
                employeeRepository.findByStatus(EmployeeStatus.ACTIVE, Pageable.unpaged()).getContent();

        int created = 0;
        int skipped = 0;
        for (Employee employee : actives) {
            if (existing.contains(employee.getId())) {
                skipped++;
                continue;
            }
            Payroll payslip = new Payroll();
            payslip.setEmployee(employee);
            payslip.setPayYear(year);
            payslip.setPayMonth(month);
            payrollRepository.findFirstByEmployee_IdOrderByPayYearDescPayMonthDesc(employee.getId())
                    .ifPresentOrElse(
                            last -> {
                                payslip.setBasicSalary(last.getBasicSalary());
                                payslip.setAllowances(last.getAllowances());
                                payslip.setDeductions(last.getDeductions());
                                payslip.setTax(last.getTax());
                                payslip.setNetSalary(netOf(last.getBasicSalary(), last.getAllowances(),
                                        last.getDeductions(), last.getTax()));
                            },
                            () -> {
                                payslip.setBasicSalary(BigDecimal.ZERO);
                                payslip.setAllowances(BigDecimal.ZERO);
                                payslip.setDeductions(BigDecimal.ZERO);
                                payslip.setTax(BigDecimal.ZERO);
                                payslip.setNetSalary(BigDecimal.ZERO);
                            });
            payrollRepository.save(payslip);
            created++;
        }

        List<Object[]> totals = payrollRepository.totalsForPeriod(year, month);
        long periodCount = ((Number) totals.get(0)[0]).longValue();
        BigDecimal periodNet = (BigDecimal) totals.get(0)[1];
        return new PayrollDto.RunResponse(year, month, created, skipped, periodCount, periodNet);
    }

    // ------------------------------------------------------------ lifecycle

    /** Overwrite the four editable components; net is always recomputed. */
    @Transactional
    public PayrollDto.PayrollRow updateComponents(Long id, PayrollDto.ComponentUpdateRequest request) {
        Payroll payslip = get(id);
        assertEditable(payslip);
        payslip.setBasicSalary(request.basicSalary());
        payslip.setAllowances(request.allowances());
        payslip.setDeductions(request.deductions());
        payslip.setTax(request.tax());
        payslip.setNetSalary(netOf(payslip.getBasicSalary(), payslip.getAllowances(),
                payslip.getDeductions(), payslip.getTax()));
        return toRow(payrollRepository.save(payslip));
    }

    @Transactional
    public PayrollDto.PayrollRow markProcessed(Long id) {
        Payroll payslip = get(id);
        if (payslip.getStatus() != Payroll.PayrollStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT payslips can be marked PROCESSED");
        }
        payslip.setStatus(Payroll.PayrollStatus.PROCESSED);
        return toRow(payrollRepository.save(payslip));
    }

    @Transactional
    public PayrollDto.PayrollRow markPaid(Long id) {
        Payroll payslip = get(id);
        if (payslip.getStatus() != Payroll.PayrollStatus.PROCESSED) {
            throw new IllegalStateException("Only PROCESSED payslips can be marked PAID");
        }
        payslip.setStatus(Payroll.PayrollStatus.PAID);
        return toRow(payrollRepository.save(payslip));
    }

    /** PAID payslips are permanent financial records — never deletable. */
    @Transactional
    public void delete(Long id) {
        Payroll payslip = get(id);
        if (payslip.getStatus() == Payroll.PayrollStatus.PAID) {
            throw new IllegalStateException("PAID payslips are permanent and cannot be deleted");
        }
        payrollRepository.delete(payslip);
    }

    // ------------------------------------------------------------ views

    @Transactional(readOnly = true)
    public PayrollDto.PeriodResponse period(int year, int month) {
        validatePeriod(year, month);
        List<Payroll> rows = payrollRepository.findForPeriodWithEmployee(year, month);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        long drafts = 0;
        long processed = 0;
        long paid = 0;
        for (Payroll row : rows) {
            totalGross = totalGross.add(row.getBasicSalary()).add(row.getAllowances());
            totalNet = totalNet.add(row.getNetSalary());
            switch (row.getStatus()) {
                case DRAFT -> drafts++;
                case PROCESSED -> processed++;
                case PAID -> paid++;
            }
        }
        PayrollDto.PeriodSummary summary = new PayrollDto.PeriodSummary(
                year, month, rows.size(), totalGross, totalNet, drafts, processed, paid);
        List<PayrollDto.PayrollRow> payslips = rows.stream().map(PayrollService::toRow).toList();
        return new PayrollDto.PeriodResponse(summary, payslips);
    }

    /** All periods that have payroll data, newest first (runs overview). */
    @Transactional(readOnly = true)
    public PayrollDto.PeriodsResponse periods() {
        List<PayrollDto.PeriodRow> rows = payrollRepository.periodTotals().stream()
                .map(row -> new PayrollDto.PeriodRow(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).longValue(),
                        (BigDecimal) row[3]))
                .toList();
        return new PayrollDto.PeriodsResponse(rows, LocalDateTime.now());
    }

    // ------------------------------------------------------------ helpers

    private Payroll get(Long id) {
        return payrollRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payslip not found: " + id));
    }

    private void assertEditable(Payroll payslip) {
        if (payslip.getStatus() == Payroll.PayrollStatus.PAID) {
            throw new IllegalStateException("PAID payslips cannot be edited");
        }
    }

    /** net = basic + allowances − deductions − tax, clamped at zero. */
    static BigDecimal netOf(BigDecimal basic, BigDecimal allowances, BigDecimal deductions, BigDecimal tax) {
        BigDecimal net = basic.add(allowances).subtract(deductions).subtract(tax);
        return net.max(BigDecimal.ZERO);
    }

    /** Shared range validation for run/period inputs. */
    private static void validatePeriod(int year, int month) {
        if (year < 2000 || year > 2999) {
            throw new IllegalArgumentException("Year out of range: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month out of range: " + month);
        }
        LocalDate now = LocalDate.now();
        if (year > now.getYear() || (year == now.getYear() && month > now.getMonthValue())) {
            throw new IllegalArgumentException("Cannot run payroll for a future period: " + year + "-" + month);
        }
    }

    private static String fullName(Employee employee) {
        String last = employee.getLastName();
        return last == null || last.isBlank()
                ? employee.getFirstName()
                : employee.getFirstName() + " " + last;
    }

    private static PayrollDto.PayrollRow toRow(Payroll payslip) {
        Employee employee = payslip.getEmployee();
        return new PayrollDto.PayrollRow(
                payslip.getId(),
                employee.getId(),
                fullName(employee),
                employee.getEmployeeCode(),
                employee.getDepartment() != null ? employee.getDepartment().getName() : null,
                payslip.getPayYear(),
                payslip.getPayMonth(),
                payslip.getBasicSalary(),
                payslip.getAllowances(),
                payslip.getDeductions(),
                payslip.getTax(),
                payslip.getNetSalary(),
                payslip.getStatus());
    }
}
