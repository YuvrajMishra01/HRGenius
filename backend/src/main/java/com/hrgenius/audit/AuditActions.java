package com.hrgenius.audit;

/**
 * The complete catalog of audited actions (Phase 18). One flat enum: a
 * closed, self-documenting vocabulary keeps the log filterable and makes
 * any new audit point an explicit, reviewable change.
 */
public enum AuditActions {

    // employee directory
    EMPLOYEE_CREATED,
    EMPLOYEE_UPDATED,
    EMPLOYEE_TERMINATED,

    // organization
    DEPARTMENT_CREATED,
    DEPARTMENT_UPDATED,
    DEPARTMENT_DELETED,
    DESIGNATION_CREATED,
    DESIGNATION_UPDATED,
    DESIGNATION_DELETED,

    // recruitment
    JOB_CREATED,
    JOB_UPDATED,
    JOB_DELETED,
    CANDIDATE_CREATED,
    CANDIDATE_UPDATED,
    CANDIDATE_DELETED,
    APPLICATION_CREATED,
    APPLICATION_MOVED,
    INTERVIEW_SCHEDULED,
    INTERVIEW_RESCHEDULED,
    INTERVIEW_COMPLETED,
    INTERVIEW_CANCELLED,

    // onboarding
    ONBOARDING_STARTED,
    ONBOARDING_FROM_APPLICATION,
    ONBOARDING_CHECKLIST_UPDATED,

    // attendance
    ATTENDANCE_MARKED,

    // leave
    LEAVE_TYPE_CREATED,
    LEAVE_TYPE_UPDATED,
    LEAVE_TYPE_DELETED,
    LEAVE_REQUEST_SUBMITTED,
    LEAVE_REQUEST_APPROVED,
    LEAVE_REQUEST_REJECTED,
    LEAVE_REQUEST_CANCELLED,
    LEAVE_REQUEST_DELETED,

    // payroll
    PAYROLL_RUN_EXECUTED,
    PAYSLIP_UPDATED,
    PAYSLIP_PROCESSED,
    PAYSLIP_PAID,
    PAYSLIP_DELETED,

    // performance
    REVIEW_CREATED,
    REVIEW_UPDATED,
    REVIEW_SUBMITTED,
    REVIEW_ACKNOWLEDGED,
    REVIEW_DELETED,

    // documents
    DOCUMENT_UPLOADED,
    DOCUMENT_DELETED
}
