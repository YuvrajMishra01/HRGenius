package com.hrgenius.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hrgenius.auth.User;
import com.hrgenius.auth.UserRepository;
import com.hrgenius.common.Lists;
import com.hrgenius.common.PageResponse;
import com.hrgenius.common.SqlPaging;
import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;
import com.hrgenius.notification.Notification;
import com.hrgenius.notification.NotificationService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Leave management (Phase 8): leave-type CRUD, request submission with
 * overlap + balance guards, manager approval workflow, and yearly balances.
 *
 * Balance accounting counts full calendar days of APPROVED requests whose
 * start date falls inside the year; working-day display excludes weekends.
 *
 * Error contract: EntityNotFoundException → 404, IllegalArgumentException
 * → 400, IllegalStateException → 409 (via GlobalExceptionHandler).
 */
@Service
public class LeaveService {

    private static final Set<LeaveRequest.LeaveStatus> LIVE_STATUSES =
            Set.of(LeaveRequest.LeaveStatus.PENDING, LeaveRequest.LeaveStatus.APPROVED);

    private final LeaveRequestRepository requestRepository;
    private final LeaveTypeRepository typeRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final NotificationService notifications;

    public LeaveService(LeaveRequestRepository requestRepository,
                        LeaveTypeRepository typeRepository,
                        EmployeeRepository employeeRepository,
                        UserRepository userRepository,
                        NotificationService notifications) {
        this.requestRepository = requestRepository;
        this.typeRepository = typeRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------ types

    @Transactional(readOnly = true)
    public List<LeaveDto.LeaveTypeResponse> listTypes() {
        return typeRepository.findAll().stream()
                .sorted(Comparator.comparing(LeaveType::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toTypeResponse)
                .toList();
    }

    @Transactional
    public LeaveDto.LeaveTypeResponse createType(LeaveDto.LeaveTypeRequest request) {
        if (typeRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new IllegalStateException("Leave type already exists: " + request.name());
        }
        LeaveType type = new LeaveType();
        applyType(type, request);
        return toTypeResponse(typeRepository.save(type));
    }

    @Transactional
    public LeaveDto.LeaveTypeResponse updateType(Long id, LeaveDto.LeaveTypeRequest request) {
        LeaveType type = typeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leave type not found: " + id));
        typeRepository.findByNameIgnoreCase(request.name().trim())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalStateException("Leave type already exists: " + request.name());
                });
        applyType(type, request);
        return toTypeResponse(typeRepository.save(type));
    }

    @Transactional
    public void deleteType(Long id) {
        LeaveType type = typeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leave type not found: " + id));
        if (requestRepository.countByLeaveTypeId(id) > 0) {
            throw new IllegalStateException("Cannot delete a leave type that has requests");
        }
        typeRepository.delete(type);
    }

    // ------------------------------------------------------------ requests

    /**
     * DB-side paged rich list (Phase 17): status + search filters and the
     * page window are computed in SQL; rows are fetched with their detail
     * joins inside the same query so DTO mapping stays lazy-safe. The
     * count twin is filter-exact, so totals always describe the filtered set.
     */
    @Transactional(readOnly = true)
    public PageResponse<LeaveDto.LeaveRequestResponse> listRequests(LeaveRequest.LeaveStatus status,
                                                                    String search, Integer page, Integer size) {
        String pattern = SqlPaging.likeEscapeOrNull(search);
        Pageable pageable = PageRequest.of(Lists.cleanPage(page), Lists.cleanSize(size));
        return SqlPaging.of(requestRepository.findPagedWithDetails(status, pattern, pageable)
                .map(LeaveService::toResponse));
    }

    @Transactional
    public LeaveDto.LeaveRequestResponse create(LeaveDto.CreateLeaveRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + request.employeeId()));
        LeaveType type = typeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new EntityNotFoundException("Leave type not found: " + request.leaveTypeId()));

        if (requestRepository.countOverlapping(employee.getId(), LIVE_STATUSES,
                request.startDate(), request.endDate()) > 0) {
            throw new IllegalStateException(
                    "Employee already has a pending or approved leave overlapping these dates");
        }
        int year = request.startDate().getYear();
        double used = usedDays(employee.getId(), type.getId(), year);
        double requested = workingDays(request.startDate(), request.endDate());
        if (used + requested > type.getYearlyLimit()) {
            throw new IllegalStateException(String.format(
                    "Insufficient balance: %s allows %d days per year, %.1f already used, %.1f requested",
                    type.getName(), type.getYearlyLimit(), used, requested));
        }

        LeaveRequest entity = new LeaveRequest();
        entity.setEmployee(employee);
        entity.setLeaveType(type);
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setReason(request.reason());
        entity.setStatus(LeaveRequest.LeaveStatus.PENDING);
        LeaveRequest saved = requestRepository.save(entity);
        notifications.notifyEmployee(employee, Notification.NotificationType.LEAVE,
                "Leave request submitted",
                "Your " + type.getName() + " request for " + saved.getStartDate() + " to "
                        + saved.getEndDate() + " is pending approval.");
        return toResponse(saved);
    }

    /** Approve or reject a PENDING request; body is empty — decision is in the path. */
    @Transactional
    public LeaveDto.LeaveRequestResponse decide(Long id, LeaveRequest.LeaveStatus target) {
        LeaveRequest entity = requestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + id));
        if (entity.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be " + target.name().toLowerCase());
        }
        // Re-check balance at approval time: another approval may have consumed days meanwhile.
        if (target == LeaveRequest.LeaveStatus.APPROVED) {
            double used = usedDays(entity.getEmployee().getId(), entity.getLeaveType().getId(),
                    entity.getStartDate().getYear());
            double requested = workingDays(entity.getStartDate(), entity.getEndDate());
            if (used + requested > entity.getLeaveType().getYearlyLimit()) {
                throw new IllegalStateException(String.format(
                        "Cannot approve: only %.1f days remain of %s's yearly limit",
                        entity.getLeaveType().getYearlyLimit() - used, entity.getLeaveType().getName()));
            }
        }
        entity.setStatus(target);
        entity.setApprovedBy(currentUser());
        LeaveRequest saved = requestRepository.save(entity);
        notifications.notifyEmployee(entity.getEmployee(), Notification.NotificationType.LEAVE,
                "Leave request " + target.name().toLowerCase(),
                "Your " + entity.getLeaveType().getName() + " request for " + saved.getStartDate()
                        + " to " + saved.getEndDate() + " was " + target.name().toLowerCase() + ".");
        return toResponse(saved);
    }

    /** Withdraw a PENDING request (back to CANCELLED, never deleted). */
    @Transactional
    public void cancel(Long id) {
        LeaveRequest entity = requestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + id));
        if (entity.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be cancelled");
        }
        entity.setStatus(LeaveRequest.LeaveStatus.CANCELLED);
        entity.setApprovedBy(null);
        requestRepository.save(entity);
    }

    /** Hard delete for decided requests only (audit-safe). */
    @Transactional
    public void delete(Long id) {
        LeaveRequest entity = requestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leave request not found: " + id));
        if (entity.getStatus() == LeaveRequest.LeaveStatus.PENDING) {
            throw new IllegalStateException("Cancel the pending request instead of deleting it");
        }
        requestRepository.delete(entity);
    }

    // ------------------------------------------------------------ balances & summary

    @Transactional(readOnly = true)
    public LeaveDto.BalanceResponse balances(Long employeeId, Integer yearParam) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeId));
        int year = yearParam != null ? yearParam : LocalDate.now().getYear();

        Map<Long, Double> usedByType = approvedDaysByType(employeeId, year);
        List<LeaveDto.BalanceRow> balances = typeRepository.findAll().stream()
                .map(type -> {
                    double used = usedByType.getOrDefault(type.getId(), 0.0);
                    return new LeaveDto.BalanceRow(type.getId(), type.getName(), type.getYearlyLimit(),
                            used, Math.max(0, type.getYearlyLimit() - used));
                })
                .toList();
        return new LeaveDto.BalanceResponse(employee.getId(), fullName(employee),
                employee.getEmployeeCode(), year, balances);
    }

    @Transactional(readOnly = true)
    public LeaveDto.LeaveSummary summary() {
        int year = LocalDate.now().getYear();
        long pending = requestRepository.countByStatus(LeaveRequest.LeaveStatus.PENDING);
        long approved = requestRepository.countByStatusBetweenStartDates(LeaveRequest.LeaveStatus.APPROVED,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        long rejected = requestRepository.countByStatus(LeaveRequest.LeaveStatus.REJECTED);
        long decided = requestRepository.countByStatusIn(List.of(
                LeaveRequest.LeaveStatus.APPROVED, LeaveRequest.LeaveStatus.REJECTED));
        BigDecimal rate = decided == 0 ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(approved * 100.0 / decided).setScale(1, RoundingMode.HALF_UP);
        return new LeaveDto.LeaveSummary(pending, approved, rejected, rate);
    }

    // ------------------------------------------------------------ helpers

    /** Current authenticated user's entity, for the APPROVED_BY audit column. */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails details) {
            return userRepository.findByEmailIgnoreCase(details.getUsername()).orElse(null);
        }
        return null;
    }

    /** APPROVED days for one employee/type/year (0 when none). */
    private double usedDays(Long employeeId, Long leaveTypeId, int year) {
        return approvedDaysByType(employeeId, year).getOrDefault(leaveTypeId, 0.0);
    }

    /** APPROVED calendar days per type, summed in Java from the range rows. */
    private Map<Long, Double> approvedDaysByType(Long employeeId, int year) {
        List<Object[]> rows = requestRepository.approvedRanges(employeeId,
                List.of(LeaveRequest.LeaveStatus.APPROVED),
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        Map<Long, Double> used = new HashMap<>();
        for (Object[] row : rows) {
            Long typeId = ((Number) row[0]).longValue();
            double days = java.time.temporal.ChronoUnit.DAYS.between((LocalDate) row[1], (LocalDate) row[2]) + 1;
            used.merge(typeId, days, Double::sum);
        }
        return used;
    }

    /** Weekday count (Mon–Fri) between the two dates, inclusive. */
    static double workingDays(LocalDate start, LocalDate end) {
        double days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            switch (d.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> { /* weekend: not counted */ }
                default -> days += 1;
            }
        }
        return days;
    }

    private void applyType(LeaveType type, LeaveDto.LeaveTypeRequest request) {
        type.setName(request.name().trim());
        type.setDescription(request.description());
        type.setYearlyLimit(request.yearlyLimit());
    }

    private LeaveDto.LeaveTypeResponse toTypeResponse(LeaveType type) {
        return new LeaveDto.LeaveTypeResponse(type.getId(), type.getName(), type.getDescription(),
                type.getYearlyLimit(), requestRepository.countByLeaveTypeId(type.getId()));
    }

    private static String fullName(Employee employee) {
        String last = employee.getLastName();
        return last == null || last.isBlank()
                ? employee.getFirstName()
                : employee.getFirstName() + " " + last;
    }

    private static LeaveDto.LeaveRequestResponse toResponse(LeaveRequest entity) {
        Employee employee = entity.getEmployee();
        User approver = entity.getApprovedBy();
        return new LeaveDto.LeaveRequestResponse(
                entity.getId(),
                employee.getId(),
                fullName(employee),
                employee.getEmployeeCode(),
                employee.getDepartment() != null ? employee.getDepartment().getName() : null,
                entity.getLeaveType().getId(),
                entity.getLeaveType().getName(),
                entity.getStartDate(),
                entity.getEndDate(),
                workingDays(entity.getStartDate(), entity.getEndDate()),
                entity.getReason(),
                entity.getStatus(),
                approver != null ? approver.getEmail() : null,
                entity.getCreatedAt());
    }
}
