package com.hrgenius.recruitment;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hrgenius.common.Lists;
import com.hrgenius.common.PageResponse;
import com.hrgenius.common.SqlPaging;
import com.hrgenius.department.Department;
import com.hrgenius.department.DepartmentRepository;
import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Recruitment module business logic (Phase 5): jobs, candidates,
 * applications with explicit pipeline transitions, and interviews.
 *
 * Error contract: EntityNotFoundException → 404, IllegalArgumentException
 * → 400, IllegalStateException → 409 (via GlobalExceptionHandler).
 */
@Service
public class RecruitmentService {

    private static final Map<ApplicationStatus, List<ApplicationStatus>> TRANSITIONS = Map.of(
            ApplicationStatus.APPLIED, List.of(ApplicationStatus.SCREENING),
            ApplicationStatus.SCREENING, List.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED),
            ApplicationStatus.SHORTLISTED, List.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED),
            ApplicationStatus.INTERVIEW, List.of(ApplicationStatus.SELECTED, ApplicationStatus.REJECTED),
            ApplicationStatus.SELECTED, List.of(),
            ApplicationStatus.REJECTED, List.of());

    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public RecruitmentService(JobRepository jobRepository,
                              CandidateRepository candidateRepository,
                              JobApplicationRepository applicationRepository,
                              InterviewRepository interviewRepository,
                              DepartmentRepository departmentRepository,
                              EmployeeRepository employeeRepository) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    // ============================================================== jobs

    /**
     * DB-side paged job list (Phase 17): status + search filters, the
     * per-job application count and the page window are all computed in
     * SQL — scale is a database concern, not a heap one. Title ordering is
     * now the DB's case-insensitive lower(title) sort.
     */
    @Transactional(readOnly = true)
    public PageResponse<RecruitmentDto.JobResponse> listJobs(String search, JobStatus status,
                                                             Integer page, Integer size) {
        String pattern = SqlPaging.likeEscapeOrNull(search);
        Pageable pageable = PageRequest.of(Lists.cleanPage(page), Lists.cleanSize(size));
        return SqlPaging.of(jobRepository.findPagedProjected(status, pattern, pageable)
                .map(RecruitmentService::toJobRow));
    }

    private static RecruitmentDto.JobResponse toJobRow(Object[] row) {
        return new RecruitmentDto.JobResponse(
                (Long) row[0], (String) row[1], (String) row[2],
                (Long) row[3], (String) row[4],
                (String) row[5],
                row[6] == null ? null : row[6].toString(),
                (String) row[7], (JobStatus) row[8],
                (java.time.LocalDate) row[9], (java.time.LocalDate) row[10],
                (Long) row[11]);
    }

    /** Pipeline stage → application count (KPI strip; never paginated). */
    @Transactional(readOnly = true)
    public Map<ApplicationStatus, Long> applicationCounts() {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        applicationRepository.countByStatusRaw()
                .forEach(row -> counts.put((ApplicationStatus) row[0], (Long) row[1]));
        return counts;
    }

    @Transactional
    public RecruitmentDto.JobResponse createJob(RecruitmentDto.JobRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + request.departmentId()));
        Job job = new Job();
        applyJobFields(job, request, department);
        return toJobResponse(job, 0L);
    }

    @Transactional
    public RecruitmentDto.JobResponse updateJob(Long id, RecruitmentDto.JobRequest request) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + id));
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + request.departmentId()));
        applyJobFields(job, request, department);
        return toJobResponse(job, countsFor(job.getId()));
    }

    /** Delete guard: a job with applications cannot be deleted (409). */
    @Transactional
    public void deleteJob(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + id));
        if (applicationRepository.countByJob_Id(id) > 0) {
            throw new IllegalStateException("Job has applications — close it instead of deleting");
        }
        jobRepository.delete(job);
    }

    // ======================================================== candidates

    /** DB-side paged candidate list (Phase 17): newest-first ordering in SQL. */
    @Transactional(readOnly = true)
    public PageResponse<RecruitmentDto.CandidateResponse> listCandidates(String search,
                                                                         CandidateStatus status,
                                                                         Integer page, Integer size) {
        String pattern = SqlPaging.likeEscapeOrNull(search);
        Pageable pageable = PageRequest.of(Lists.cleanPage(page), Lists.cleanSize(size));
        return SqlPaging.of(candidateRepository.findPagedProjected(status, pattern, pageable)
                .map(RecruitmentService::toCandidateRow));
    }

    private static RecruitmentDto.CandidateResponse toCandidateRow(Object[] row) {
        return new RecruitmentDto.CandidateResponse(
                (Long) row[0], (String) row[1], (String) row[2], (String) row[3],
                (String) row[4], (String) row[5], (java.math.BigDecimal) row[6],
                (CandidateStatus) row[7], (java.time.LocalDateTime) row[8], (Long) row[9]);
    }

    @Transactional
    public RecruitmentDto.CandidateResponse createCandidate(RecruitmentDto.CandidateRequest request) {
        if (candidateRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalStateException("A candidate with this email already exists");
        }
        Candidate candidate = new Candidate();
        applyCandidateFields(candidate, request);
        candidate.setStatus(CandidateStatus.NEW);
        return toCandidateResponse(candidateRepository.save(candidate), 0L);
    }

    @Transactional
    public RecruitmentDto.CandidateResponse updateCandidate(Long id, RecruitmentDto.CandidateRequest request) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found: " + id));
        candidateRepository.findByEmailIgnoreCase(request.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalStateException("A candidate with this email already exists");
                });
        applyCandidateFields(candidate, request);
        return toCandidateResponse(candidate, countsForCandidate(id));
    }

    /** Status is pipeline-driven (applications), not free-editable. */
    private static void applyCandidateFields(Candidate candidate, RecruitmentDto.CandidateRequest request) {
        candidate.setName(request.name());
        candidate.setEmail(request.email());
        candidate.setPhone(request.phone());
        candidate.setResumePath(request.resumePath());
        candidate.setSkills(request.skills());
        candidate.setExperienceYears(request.experienceYears());
    }

    /** Delete guard: a candidate with applications cannot be deleted (409). */
    @Transactional
    public void deleteCandidate(Long id) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found: " + id));
        if (applicationRepository.countByCandidate_Id(id) > 0) {
            throw new IllegalStateException("Candidate has applications — reject them instead of deleting");
        }
        candidateRepository.delete(candidate);
    }

    // ====================================================== applications

    /** DB-side paged application list (Phase 17): id-ascending in SQL. */
    @Transactional(readOnly = true)
    public PageResponse<RecruitmentDto.ApplicationResponse> listApplications(String search,
                                                                             ApplicationStatus status,
                                                                             Integer page, Integer size) {
        String pattern = SqlPaging.likeEscapeOrNull(search);
        Pageable pageable = PageRequest.of(Lists.cleanPage(page), Lists.cleanSize(size));
        return SqlPaging.of(applicationRepository.findPagedProjected(status, pattern, pageable)
                .map(RecruitmentService::toApplicationRow));
    }

    private static RecruitmentDto.ApplicationResponse toApplicationRow(Object[] row) {
        return new RecruitmentDto.ApplicationResponse(
                (Long) row[0], (Long) row[1], (String) row[2], (String) row[3],
                (Long) row[4], (String) row[5], (String) row[6],
                (java.time.LocalDate) row[7], (ApplicationStatus) row[8], (String) row[9]);
    }

    @Transactional
    public RecruitmentDto.ApplicationResponse createApplication(RecruitmentDto.ApplicationRequest request) {
        Candidate candidate = candidateRepository.findById(request.candidateId())
                .orElseThrow(() -> new EntityNotFoundException("Candidate not found: " + request.candidateId()));
        Job job = jobRepository.findById(request.jobId())
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + request.jobId()));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new IllegalArgumentException("Applications are only accepted for OPEN jobs");
        }
        if (applicationRepository.existsByCandidate_IdAndJob_Id(request.candidateId(), request.jobId())) {
            throw new IllegalStateException("This candidate has already applied to this job");
        }
        JobApplication application = new JobApplication();
        application.setCandidate(candidate);
        application.setJob(job);
        application.setApplicationDate(LocalDate.now());
        application.setRemarks(request.remarks());
        applicationRepository.save(application);
        return toApplicationResponseProjection(List.of(applicationRepository.findById(application.getId()).orElseThrow())).get(0);
    }

    /**
     * Explicit pipeline move. Legal transitions:
     * APPLIED→SCREENING · SCREENING→SHORTLISTED|REJECTED ·
     * SHORTLISTED→INTERVIEW|REJECTED · INTERVIEW→SELECTED|REJECTED.
     * Terminal stages (SELECTED, REJECTED) cannot move. Anything illegal is 409.
     */
    @Transactional
    public RecruitmentDto.ApplicationResponse transitionApplication(Long id, RecruitmentDto.TransitionRequest request) {
        JobApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + id));
        ApplicationStatus current = application.getStatus();
        ApplicationStatus target = request.status();
        List<ApplicationStatus> allowed = TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalStateException("Cannot move application from " + current + " to " + target);
        }
        application.setStatus(target);
        if (request.remarks() != null && !request.remarks().isBlank()) {
            application.setRemarks(request.remarks());
        }
        applicationRepository.save(application);

        // Keep the candidate status roughly in sync with pipeline progress.
        Candidate candidate = application.getCandidate();
        switch (target) {
            case SCREENING -> candidate.setStatus(CandidateStatus.SCREENING);
            case SHORTLISTED -> candidate.setStatus(CandidateStatus.SHORTLISTED);
            case INTERVIEW -> candidate.setStatus(CandidateStatus.INTERVIEWED);
            case SELECTED -> candidate.setStatus(CandidateStatus.SELECTED);
            case REJECTED -> candidate.setStatus(CandidateStatus.REJECTED);
            default -> { /* APPLIED: no candidate-status change */ }
        }
        candidateRepository.save(candidate);
        return toApplicationResponseProjection(List.of(applicationRepository.findById(id).orElseThrow())).get(0);
    }

    // ======================================================== interviews

    /** DB-side paged interview list (Phase 17): id-ascending in SQL. */
    @Transactional(readOnly = true)
    public PageResponse<RecruitmentDto.InterviewResponse> listInterviews(String search,
                                                                         Interview.InterviewStatus status,
                                                                         Integer page, Integer size) {
        String pattern = SqlPaging.likeEscapeOrNull(search);
        Pageable pageable = PageRequest.of(Lists.cleanPage(page), Lists.cleanSize(size));
        return SqlPaging.of(interviewRepository.findPagedProjected(status, pattern, pageable)
                .map(RecruitmentService::toInterviewRow));
    }

    private static RecruitmentDto.InterviewResponse toInterviewRow(Object[] row) {
        return new RecruitmentDto.InterviewResponse(
                (Long) row[0], (Long) row[1], (String) row[2], (String) row[3],
                (Long) row[4], (String) row[5], (java.time.OffsetDateTime) row[6],
                (Interview.InterviewMode) row[7], (Interview.InterviewStatus) row[8],
                (String) row[9], (Interview.InterviewResult) row[10]);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Scheduling an interview for a SHORTLISTED application auto-advances it to
     * INTERVIEW; for an INTERVIEW application it simply adds another round.
     * One live (SCHEDULED) interview per application is enforced.
     */
    @Transactional
    public RecruitmentDto.InterviewResponse createInterview(RecruitmentDto.InterviewRequest request) {
        JobApplication application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + request.applicationId()));
        if (application.getStatus() != ApplicationStatus.SHORTLISTED
                && application.getStatus() != ApplicationStatus.INTERVIEW) {
            throw new IllegalStateException(
                    "Interviews can only be scheduled for SHORTLISTED or INTERVIEW applications");
        }
        if (request.interviewDate().isBefore(java.time.OffsetDateTime.now())) {
            throw new IllegalArgumentException("Interview date must be in the future");
        }
        if (interviewRepository.existsByApplication_IdAndStatus(request.applicationId(),
                Interview.InterviewStatus.SCHEDULED)) {
            throw new IllegalStateException("This application already has a scheduled interview");
        }
        Interview interview = new Interview();
        interview.setApplication(application);
        interview.setInterviewer(resolveInterviewer(request.interviewerId()));
        interview.setInterviewDate(request.interviewDate());
        interview.setMode(request.mode());
        interviewRepository.save(interview);

        if (application.getStatus() == ApplicationStatus.SHORTLISTED) {
            application.setStatus(ApplicationStatus.INTERVIEW);
            applicationRepository.save(application);
            syncCandidate(application.getCandidate(), ApplicationStatus.INTERVIEW);
        }
        return toInterviewResponseProjection(List.of(interviewRepository.findById(interview.getId()).orElseThrow())).get(0);
    }

    @Transactional
    public RecruitmentDto.InterviewResponse rescheduleInterview(Long id, RecruitmentDto.InterviewRescheduleRequest request) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + id));
        if (interview.getStatus() != Interview.InterviewStatus.SCHEDULED) {
            throw new IllegalStateException("Only SCHEDULED interviews can be rescheduled");
        }
        if (request.interviewDate() != null) {
            if (request.interviewDate().isBefore(java.time.OffsetDateTime.now())) {
                throw new IllegalArgumentException("Interview date must be in the future");
            }
            interview.setInterviewDate(request.interviewDate());
        }
        if (request.mode() != null) {
            interview.setMode(request.mode());
        }
        if (request.interviewerId() != null) {
            interview.setInterviewer(resolveInterviewer(request.interviewerId()));
        }
        return toInterviewResponseProjection(List.of(interview)).get(0);
    }

    /** Completing an interview records feedback + result but does NOT auto-move the pipeline. */
    @Transactional
    public RecruitmentDto.InterviewResponse completeInterview(Long id, RecruitmentDto.InterviewFeedbackRequest request) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + id));
        if (interview.getStatus() != Interview.InterviewStatus.SCHEDULED) {
            throw new IllegalStateException("Only SCHEDULED interviews can be completed");
        }
        interview.setStatus(Interview.InterviewStatus.COMPLETED);
        interview.setFeedback(request.feedback());
        interview.setResult(request.result());
        return toInterviewResponseProjection(List.of(interview)).get(0);
    }

    @Transactional
    public RecruitmentDto.InterviewResponse cancelInterview(Long id) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + id));
        if (interview.getStatus() != Interview.InterviewStatus.SCHEDULED) {
            throw new IllegalStateException("Only SCHEDULED interviews can be cancelled");
        }
        interview.setStatus(Interview.InterviewStatus.CANCELLED);
        return toInterviewResponseProjection(List.of(interview)).get(0);
    }

    // ============================================================ shared

    private Employee resolveInterviewer(Long interviewerId) {
        if (interviewerId == null) {
            return null;
        }
        return employeeRepository.findById(interviewerId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + interviewerId));
    }

    private long countsFor(Long jobId) {
        return applicationRepository.countByJob_Id(jobId);
    }

    private long countsForCandidate(Long candidateId) {
        return applicationRepository.countByCandidate_Id(candidateId);
    }

    private void syncCandidate(Candidate candidate, ApplicationStatus target) {
        switch (target) {
            case INTERVIEW -> candidate.setStatus(CandidateStatus.INTERVIEWED);
            default -> { /* others handled at transition time */ }
        }
    }

    private void applyJobFields(Job job, RecruitmentDto.JobRequest request, Department department) {
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setDepartment(department);
        job.setLocation(request.location());
        job.setEmploymentType(request.employmentType());
        job.setSalaryRange(request.salaryRange());
        job.setStatus(request.status());
        job.setClosingDate(request.closingDate());
        if (request.status() == JobStatus.OPEN && job.getPostedDate() == null) {
            job.setPostedDate(LocalDate.now());
        }
        jobRepository.save(job);
    }

    // ====================================================== DTO mapping

    private static RecruitmentDto.JobResponse toJobResponse(Job job, long applicationCount) {
        return new RecruitmentDto.JobResponse(
                job.getId(), job.getTitle(), job.getDescription(),
                job.getDepartment() != null ? job.getDepartment().getId() : null,
                job.getDepartment() != null ? job.getDepartment().getName() : null,
                job.getLocation(),
                job.getEmploymentType() != null ? job.getEmploymentType().name() : null,
                job.getSalaryRange(), job.getStatus(), job.getPostedDate(), job.getClosingDate(),
                applicationCount);
    }

    private static RecruitmentDto.CandidateResponse toCandidateResponse(Object[] row) {
        return new RecruitmentDto.CandidateResponse(
                (Long) row[0], (String) row[1], (String) row[2], (String) row[3],
                (String) row[4], (String) row[5], (java.math.BigDecimal) row[6],
                (CandidateStatus) row[7], (java.time.LocalDateTime) row[8], (Long) row[9]);
    }

    private static RecruitmentDto.CandidateResponse toCandidateResponse(Candidate candidate, long applicationCount) {
        return new RecruitmentDto.CandidateResponse(
                candidate.getId(), candidate.getName(), candidate.getEmail(), candidate.getPhone(),
                candidate.getResumePath(), candidate.getSkills(), candidate.getExperienceYears(),
                candidate.getStatus(), candidate.getCreatedAt(), applicationCount);
    }

    private static List<RecruitmentDto.ApplicationResponse> toApplicationResponseProjection(List<JobApplication> applications) {
        // Entity-based mapping: lazy proxies are already resolved inside the transaction.
        return applications.stream()
                .map(a -> new RecruitmentDto.ApplicationResponse(
                        a.getId(),
                        a.getCandidate().getId(), a.getCandidate().getName(), a.getCandidate().getEmail(),
                        a.getJob().getId(), a.getJob().getTitle(),
                        a.getJob().getDepartment() != null ? a.getJob().getDepartment().getName() : null,
                        a.getApplicationDate(), a.getStatus(), a.getRemarks()))
                .toList();
    }

    private static RecruitmentDto.ApplicationResponse toApplicationResponse(Object[] row) {
        return new RecruitmentDto.ApplicationResponse(
                (Long) row[0], (Long) row[1], (String) row[2], (String) row[3],
                (Long) row[4], (String) row[5], (String) row[6],
                (java.time.LocalDate) row[7], (ApplicationStatus) row[8], (String) row[9]);
    }

    private static List<RecruitmentDto.InterviewResponse> toInterviewResponseProjection(List<Interview> interviews) {
        return interviews.stream()
                .map(i -> new RecruitmentDto.InterviewResponse(
                        i.getId(), i.getApplication().getId(),
                        i.getApplication().getCandidate().getName(),
                        i.getApplication().getJob().getTitle(),
                        i.getInterviewer() != null ? i.getInterviewer().getId() : null,
                        i.getInterviewer() != null
                                ? i.getInterviewer().getFirstName() + " " + i.getInterviewer().getLastName()
                                : null,
                        i.getInterviewDate(), i.getMode(), i.getStatus(), i.getFeedback(), i.getResult()))
                .toList();
    }

    private static RecruitmentDto.InterviewResponse toInterviewResponse(Object[] row) {
        return new RecruitmentDto.InterviewResponse(
                (Long) row[0], (Long) row[1], (String) row[2], (String) row[3],
                (Long) row[4], (String) row[5], (java.time.OffsetDateTime) row[6],
                (Interview.InterviewMode) row[7], (Interview.InterviewStatus) row[8],
                (String) row[9], (Interview.InterviewResult) row[10]);
    }
}
