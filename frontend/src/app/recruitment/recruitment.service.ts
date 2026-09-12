import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../core/api.models';

import {
  ApplicationRequest,
  Candidate,
  CandidateRequest,
  EmployeeOption,
  Interview,
  InterviewFeedbackRequest,
  InterviewRequest,
  InterviewRescheduleRequest,
  Job,
  JobApplication,
  JobRequest,
} from './recruitment.models';

/** Data access for the recruitment module (Phase 5). */
@Injectable({ providedIn: 'root' })
export class RecruitmentService {
  private readonly http = inject(HttpClient);

  // ------------------------------------------------------------- jobs
  jobs(): Observable<ApiResponse<Job[]>> {
    return this.http.get<ApiResponse<Job[]>>('/api/v1/jobs');
  }

  createJob(request: JobRequest): Observable<ApiResponse<Job>> {
    return this.http.post<ApiResponse<Job>>('/api/v1/jobs', request);
  }

  updateJob(id: number, request: JobRequest): Observable<ApiResponse<Job>> {
    return this.http.put<ApiResponse<Job>>(`/api/v1/jobs/${id}`, request);
  }

  deleteJob(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`/api/v1/jobs/${id}`);
  }

  // -------------------------------------------------------- candidates
  candidates(): Observable<ApiResponse<Candidate[]>> {
    return this.http.get<ApiResponse<Candidate[]>>('/api/v1/candidates');
  }

  createCandidate(request: CandidateRequest): Observable<ApiResponse<Candidate>> {
    return this.http.post<ApiResponse<Candidate>>('/api/v1/candidates', request);
  }

  deleteCandidate(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`/api/v1/candidates/${id}`);
  }

  // ------------------------------------------------------ applications
  applications(): Observable<ApiResponse<JobApplication[]>> {
    return this.http.get<ApiResponse<JobApplication[]>>('/api/v1/applications');
  }

  createApplication(request: ApplicationRequest): Observable<ApiResponse<JobApplication>> {
    return this.http.post<ApiResponse<JobApplication>>('/api/v1/applications', request);
  }

  transition(id: number, status: string, remarks: string | null): Observable<ApiResponse<JobApplication>> {
    return this.http.patch<ApiResponse<JobApplication>>(`/api/v1/applications/${id}/status`, {
      status,
      remarks,
    });
  }

  // -------------------------------------------------------- interviews
  interviews(): Observable<ApiResponse<Interview[]>> {
    return this.http.get<ApiResponse<Interview[]>>('/api/v1/interviews');
  }

  createInterview(request: InterviewRequest): Observable<ApiResponse<Interview>> {
    return this.http.post<ApiResponse<Interview>>('/api/v1/interviews', request);
  }

  rescheduleInterview(id: number, request: InterviewRescheduleRequest): Observable<ApiResponse<Interview>> {
    return this.http.patch<ApiResponse<Interview>>(`/api/v1/interviews/${id}`, request);
  }

  completeInterview(id: number, request: InterviewFeedbackRequest): Observable<ApiResponse<Interview>> {
    return this.http.post<ApiResponse<Interview>>(`/api/v1/interviews/${id}/complete`, request);
  }

  cancelInterview(id: number): Observable<ApiResponse<Interview>> {
    return this.http.post<ApiResponse<Interview>>(`/api/v1/interviews/${id}/cancel`, null);
  }

  // ------------------------------------------------------------ shared
  departments(): Observable<ApiResponse<{ id: number; name: string }[]>> {
    return this.http.get<ApiResponse<{ id: number; name: string }[]>>('/api/v1/departments');
  }

  /** Employee options for interviewer pickers. */
  employeeOptions(): Observable<ApiResponse<EmployeeOption[]>> {
    return this.http.get<ApiResponse<EmployeeOption[]>>('/api/v1/employees/options');
  }
}
