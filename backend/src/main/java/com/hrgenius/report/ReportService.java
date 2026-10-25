package com.hrgenius.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.hrgenius.attendance.Attendance;
import com.hrgenius.attendance.AttendanceRepository;
import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;
import com.hrgenius.employee.EmployeeSpecifications;
import com.hrgenius.employee.EmployeeStatus;
import com.hrgenius.leave.LeaveRequest;
import com.hrgenius.leave.LeaveRequestRepository;
import com.hrgenius.payroll.Payroll;
import com.hrgenius.payroll.PayrollRepository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report data assembly (Phase 15). Each export re-uses the module's own
 * detail-fetching queries so the numbers always match what the UI shows;
 * this class only projects rows into header/columns form for the CSV and
 * PDF builders. Everything is read-only.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EmployeeRepository employees;
    private final PayrollRepository payrolls;
    private final AttendanceRepository attendance;
    private final LeaveRequestRepository leaveRequests;

    public ReportService(EmployeeRepository employees,
                         PayrollRepository payrolls,
                         AttendanceRepository attendance,
                         LeaveRequestRepository leaveRequests) {
        this.employees = employees;
        this.payrolls = payrolls;
        this.attendance = attendance;
        this.leaveRequests = leaveRequests;
    }

    // ----------------------------------------------------------- employees

    @Transactional(readOnly = true)
    public List<List<String>> employeeRows(String search, String status) {
        Specification<Employee> spec = Specification.where(null);
        if (search != null && !search.isBlank()) {
            spec = spec.and(EmployeeSpecifications.search(search.trim()));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and(EmployeeSpecifications.withStatus(
                    EmployeeStatus.valueOf(status.trim().toUpperCase())));
        }
        List<Employee> rows = employees.findAll(spec,
                Sort.by(Sort.Direction.ASC, "employeeCode"));
        List<List<String>> out = new ArrayList<>();
        for (Employee e : rows) {
            out.add(List.of(
                    e.getEmployeeCode(),
                    e.getFirstName() + " " + e.getLastName(),
                    e.getEmail(),
                    e.getPhone() == null ? "" : e.getPhone(),
                    e.getDepartment() == null ? "" : e.getDepartment().getName(),
                    e.getDesignation() == null ? "" : e.getDesignation().getTitle(),
                    e.getEmploymentType() == null ? "" : e.getEmploymentType().name(),
                    e.getStatus() == null ? "" : e.getStatus().name(),
                    e.getJoiningDate() == null ? "" : e.getJoiningDate().toString()));
        }
        return out;
    }

    public static final List<String> EMPLOYEE_HEADERS = List.of(
            "Employee Code", "Name", "Email", "Phone", "Department", "Designation",
            "Employment Type", "Status", "Joining Date");

    // ------------------------------------------------------------- payroll

    @Transactional(readOnly = true)
    public List<List<String>> payrollRows(int year, int month) {
        List<Payroll> rows = payrolls.findForPeriodWithEmployee(year, month);
        List<List<String>> out = new ArrayList<>();
        for (Payroll p : rows) {
            Employee e = p.getEmployee();
            out.add(List.of(
                    e == null ? "" : e.getEmployeeCode(),
                    e == null ? "" : e.getFirstName() + " " + e.getLastName(),
                    e == null ? "" : e.getDepartment() == null ? "" : e.getDepartment().getName(),
                    String.valueOf(p.getPayYear()),
                    String.valueOf(p.getPayMonth()),
                    p.getBasicSalary().toPlainString(),
                    p.getAllowances().toPlainString(),
                    p.getDeductions().toPlainString(),
                    p.getTax().toPlainString(),
                    p.getNetSalary().toPlainString(),
                    p.getStatus() == null ? "" : p.getStatus().name()));
        }
        return out;
    }

    public static final List<String> PAYROLL_HEADERS = List.of(
            "Employee Code", "Name", "Department", "Year", "Month",
            "Basic", "Allowances", "Deductions", "Tax", "Net", "Status");

    // ----------------------------------------------------------- attendance

    @Transactional(readOnly = true)
    public List<List<String>> attendanceRows(LocalDate from, LocalDate to) {
        List<Attendance> rows = attendance.findMonthWithDetails(from, to);
        List<List<String>> out = new ArrayList<>();
        for (Attendance a : rows) {
            Employee e = a.getEmployee();
            out.add(List.of(
                    e == null ? "" : e.getEmployeeCode(),
                    e == null ? "" : e.getFirstName() + " " + e.getLastName(),
                    a.getAttendanceDate() == null ? "" : a.getAttendanceDate().toString(),
                    a.getStatus() == null ? "" : a.getStatus().name(),
                    a.getCheckIn() == null ? "" : a.getCheckIn().toString(),
                    a.getCheckOut() == null ? "" : a.getCheckOut().toString(),
                    a.getWorkingHours() == null ? "" : a.getWorkingHours().toPlainString()));
        }
        return out;
    }

    public static final List<String> ATTENDANCE_HEADERS = List.of(
            "Employee Code", "Name", "Date", "Status", "Check In", "Check Out", "Hours");

    // --------------------------------------------------------------- leave

    @Transactional(readOnly = true)
    public List<List<String>> leaveRows(String status) {
        List<LeaveRequest> rows = status == null || status.isBlank()
                ? leaveRequests.findAllWithDetails()
                : leaveRequests.findByStatusWithDetails(
                        LeaveRequest.LeaveStatus.valueOf(status.trim().toUpperCase()));
        List<List<String>> out = new ArrayList<>();
        for (LeaveRequest r : rows) {
            Employee e = r.getEmployee();
            out.add(List.of(
                    e == null ? "" : e.getEmployeeCode(),
                    e == null ? "" : e.getFirstName() + " " + e.getLastName(),
                    r.getLeaveType() == null ? "" : r.getLeaveType().getName(),
                    r.getStartDate() == null ? "" : r.getStartDate().toString(),
                    r.getEndDate() == null ? "" : r.getEndDate().toString(),
                    r.getReason() == null ? "" : r.getReason(),
                    r.getStatus() == null ? "" : r.getStatus().name(),
                    r.getCreatedAt() == null ? "" : r.getCreatedAt().format(STAMP)));
        }
        return out;
    }

    public static final List<String> LEAVE_HEADERS = List.of(
            "Employee Code", "Name", "Leave Type", "Start", "End", "Reason", "Status", "Requested At");

    // -------------------------------------------------------------- shared

    public static String stamp() {
        return LocalDateTime.now().format(STAMP);
    }
}
