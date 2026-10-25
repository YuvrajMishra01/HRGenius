/** Mirrors backend recruitment module DTOs (Phase 5). */

export type JobStatus = 'DRAFT' | 'OPEN' | 'CLOSED';
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERN';
export type CandidateStatus =
  | 'NEW'
  | 'SCREENING'
  | 'SHORTLISTED'
  | 'INTERVIEWED'
  | 'SELECTED'
  | 'REJECTED'
  | 'HIRED';
export type ApplicationStatus =
  | 'APPLIED'
  | 'SCREENING'
  | 'SHORTLISTED'
  | 'INTERVIEW'
  | 'SELECTED'
  | 'REJECTED';
export type InterviewMode = 'ONSITE' | 'ONLINE' | 'PHONE';
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
export type InterviewResult = 'PASS' | 'FAIL' | 'ON_HOLD';

export interface Job {
  id: number;
  title: string;
  description: string | null;
  departmentId: number;
  departmentName: string;
  location: string | null;
  employmentType: EmploymentType | null;
  salaryRange: string | null;
  status: JobStatus;
  postedDate: string | null;
  closingDate: string | null;
  applicationCount: number;
}

export interface JobRequest {
  title: string;
  description: string | null;
  departmentId: number;
  location: string | null;
  employmentType: EmploymentType;
  salaryRange: string | null;
  status: JobStatus;
  closingDate: string | null;
}

export interface Candidate {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  resumePath: string | null;
  skills: string | null;
  experienceYears: number | null;
  status: CandidateStatus;
  createdAt: string | null;
  applicationCount: number;
}

export interface CandidateRequest {
  name: string;
  email: string;
  phone: string | null;
  resumePath: string | null;
  skills: string | null;
  experienceYears: number | null;
}

export interface JobApplication {
  id: number;
  candidateId: number;
  candidateName: string;
  candidateEmail: string;
  jobId: number;
  jobTitle: string;
  departmentName: string | null;
  applicationDate: string;
  status: ApplicationStatus;
  remarks: string | null;
}

export interface ApplicationRequest {
  candidateId: number;
  jobId: number;
  remarks: string | null;
}

/** Legal pipeline moves; anything else is rejected by the backend (409). */
export const NEXT_STAGES: Record<ApplicationStatus, ApplicationStatus[]> = {
  APPLIED: ['SCREENING'],
  SCREENING: ['SHORTLISTED', 'REJECTED'],
  SHORTLISTED: ['INTERVIEW', 'REJECTED'],
  INTERVIEW: ['SELECTED', 'REJECTED'],
  SELECTED: [],
  REJECTED: [],
};

export interface Interview {
  id: number;
  applicationId: number;
  candidateName: string;
  jobTitle: string;
  interviewerId: number | null;
  interviewerName: string | null;
  interviewDate: string;
  mode: InterviewMode;
  status: InterviewStatus;
  feedback: string | null;
  result: InterviewResult | null;
}

export interface InterviewRequest {
  applicationId: number;
  interviewerId: number | null;
  interviewDate: string; // ISO with offset
  mode: InterviewMode;
}

export interface InterviewRescheduleRequest {
  interviewDate?: string;
  mode?: InterviewMode;
  interviewerId?: number | null;
}

export interface InterviewFeedbackRequest {
  feedback: string | null;
  result: InterviewResult;
}

/** Lightweight employee projection used for interviewer pickers (GET /employees/options). */
export interface EmployeeOption {
  id: number;
  label: string;
}
