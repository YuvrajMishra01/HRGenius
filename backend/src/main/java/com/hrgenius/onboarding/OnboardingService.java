package com.hrgenius.onboarding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;
import com.hrgenius.employee.EmployeeStatus;
import com.hrgenius.employee.EmploymentType;
import com.hrgenius.notification.Notification;
import com.hrgenius.notification.NotificationService;
import com.hrgenius.recruitment.ApplicationStatus;
import com.hrgenius.recruitment.Candidate;
import com.hrgenius.recruitment.CandidateRepository;
import com.hrgenius.recruitment.CandidateStatus;
import com.hrgenius.recruitment.JobApplication;
import com.hrgenius.recruitment.JobApplicationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Onboarding workflow (Phase 6): turns a SELECTED application into an
 * employee + checklist-driven onboarding record, and tracks completion.
 *
 * Error contract: EntityNotFoundException → 404, IllegalArgumentException
 * → 400, IllegalStateException → 409 (via GlobalExceptionHandler).
 */
@Service
public class OnboardingService {

    private final OnboardingRepository onboardingRepository;
    private final EmployeeRepository employeeRepository;
    private final JobApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final NotificationService notifications;

    public OnboardingService(OnboardingRepository onboardingRepository,
                             EmployeeRepository employeeRepository,
                             JobApplicationRepository applicationRepository,
                             CandidateRepository candidateRepository,
                             NotificationService notifications) {
        this.onboardingRepository = onboardingRepository;
        this.employeeRepository = employeeRepository;
        this.applicationRepository = applicationRepository;
        this.candidateRepository = candidateRepository;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<OnboardingDto.OnboardingResponse> list() {
        return onboardingRepository.findAllWithDetails().stream()
                .map(OnboardingService::toResponse)
                .toList();
    }

    /**
     * Convert a SELECTED application: creates the Employee (generated unique
     * code + email, job's department, ACTIVE, full-time), marks the candidate
     * HIRED, and opens the default checklist at PENDING/0%.
     */
    @Transactional
    public OnboardingDto.OnboardingResponse startFromApplication(OnboardingDto.StartFromApplicationRequest request) {
        JobApplication application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + request.applicationId()));
        if (application.getStatus() != ApplicationStatus.SELECTED) {
            throw new IllegalStateException("Only SELECTED applications can start onboarding");
        }
        if (onboardingRepository.existsByApplication_Id(application.getId())) {
            throw new IllegalStateException("Onboarding already started for this application");
        }
        Candidate candidate = application.getCandidate();

        Employee employee = new Employee();
        employee.setEmployeeCode(nextEmployeeCode());
        splitName(candidate.getName(), employee);
        employee.setEmail(generateUniqueEmail(candidate));
        LocalDate joiningDate = request.joiningDate() != null ? request.joiningDate() : LocalDate.now();
        employee.setJoiningDate(joiningDate);
        employee.setEmploymentType(EmploymentType.FULL_TIME);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setDepartment(application.getJob().getDepartment());
        employeeRepository.save(employee);

        candidate.setStatus(CandidateStatus.HIRED);
        candidateRepository.save(candidate);

        notifications.notifyEmployee(employee, Notification.NotificationType.ONBOARDING,
                "Welcome to HRGenius",
                "Your onboarding has started. Joining date: " + joiningDate + ".");
        return createRecord(employee, application, joiningDate);
    }

    /** Onboard an existing employee (no recruitment origin) — one record per employee. */
    @Transactional
    public OnboardingDto.OnboardingResponse startForEmployee(OnboardingDto.StartForEmployeeRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + request.employeeId()));
        if (onboardingRepository.existsByEmployee_Id(employee.getId())) {
            throw new IllegalStateException("This employee already has an onboarding record");
        }
        LocalDate joiningDate = request.joiningDate() != null ? request.joiningDate() : employee.getJoiningDate();
        notifications.notifyEmployee(employee, Notification.NotificationType.ONBOARDING,
                "Onboarding started",
                "An onboarding checklist was opened for you. Joining date: " + joiningDate + ".");
        return createRecord(employee, null, joiningDate);
    }

    /** Toggle one checklist item; completion % and status are derived. */
    @Transactional
    public OnboardingDto.OnboardingResponse updateChecklist(Long id, OnboardingDto.ChecklistUpdateRequest request) {
        Onboarding onboarding = onboardingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Onboarding not found: " + id));
        List<ChecklistItem> items = onboarding.getChecklist() != null
                ? onboarding.getChecklist()
                : ChecklistItem.defaultChecklist();
        if (request.itemIndex() < 0 || request.itemIndex() >= items.size()) {
            throw new IllegalArgumentException("Invalid checklist item index: " + request.itemIndex());
        }
        List<ChecklistItem> updated = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            ChecklistItem item = items.get(i);
            updated.add(i == request.itemIndex() ? new ChecklistItem(item.label(), request.done()) : item);
        }
        onboarding.setChecklist(updated);

        long done = updated.stream().filter(ChecklistItem::done).count();
        BigDecimal pct = BigDecimal.valueOf(done * 100.0 / updated.size())
                .setScale(2, RoundingMode.HALF_UP);
        onboarding.setCompletionPercentage(pct);
        onboarding.setStatus(done == 0 ? OnboardingStatus.PENDING
                : done == updated.size() ? OnboardingStatus.COMPLETED
                : OnboardingStatus.IN_PROGRESS);
        return toResponse(onboarding);
    }

    // ------------------------------------------------------------ shared

    private OnboardingDto.OnboardingResponse createRecord(Employee employee, JobApplication application,
                                                          LocalDate joiningDate) {
        Onboarding onboarding = new Onboarding();
        onboarding.setEmployee(employee);
        onboarding.setApplication(application);
        onboarding.setJoiningDate(joiningDate);
        onboarding.setStatus(OnboardingStatus.PENDING);
        onboarding.setCompletionPercentage(BigDecimal.ZERO);
        onboarding.setChecklist(ChecklistItem.defaultChecklist());
        onboardingRepository.save(onboarding);
        return toResponse(onboarding);
    }

    /** First free EMP### code (deterministic, respects the unique constraint). */
    private String nextEmployeeCode() {
        for (int i = 1; i <= 9999; i++) {
            String code = String.format("EMP%03d", i);
            if (!employeeRepository.existsByEmployeeCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Employee code space exhausted");
    }

    /** Candidate email local-part @hrgenius.local, uniquified against EMPLOYEES. */
    private String generateUniqueEmail(Candidate candidate) {
        String base = candidate.getEmail().split("@")[0].toLowerCase().replaceAll("[^a-z0-9.]", "");
        if (base.isBlank()) {
            base = "new.hire";
        }
        String email = base + "@hrgenius.local";
        int suffix = 1;
        while (employeeRepository.existsByEmailIgnoreCase(email)) {
            email = base + suffix + "@hrgenius.local";
            suffix++;
        }
        return email;
    }

    /** First token is the first name, the rest is the last name. */
    private static void splitName(String fullName, Employee employee) {
        String name = fullName == null ? "" : fullName.trim();
        int space = name.indexOf(' ');
        if (space > 0) {
            employee.setFirstName(name.substring(0, space));
            employee.setLastName(name.substring(space + 1));
        } else {
            employee.setFirstName(name.isBlank() ? "New" : name);
            employee.setLastName("Hire");
        }
    }

    private static OnboardingDto.OnboardingResponse toResponse(Onboarding onboarding) {
        Employee employee = onboarding.getEmployee();
        JobApplication application = onboarding.getApplication();
        String fullName = employee.getLastName() == null || employee.getLastName().isBlank()
                ? employee.getFirstName()
                : employee.getFirstName() + " " + employee.getLastName();
        return new OnboardingDto.OnboardingResponse(
                onboarding.getId(),
                employee.getId(),
                fullName,
                employee.getEmployeeCode(),
                employee.getDepartment() != null ? employee.getDepartment().getName() : null,
                application != null ? application.getId() : null,
                application != null ? application.getCandidate().getName() : null,
                application != null ? application.getJob().getTitle() : null,
                onboarding.getJoiningDate(),
                onboarding.getStatus(),
                onboarding.getCompletionPercentage(),
                (onboarding.getChecklist() != null ? onboarding.getChecklist() : ChecklistItem.defaultChecklist())
                        .stream()
                        .map(item -> new OnboardingDto.ChecklistItemDto(item.label(), item.done()))
                        .toList());
    }
}
