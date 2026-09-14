import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiResponse, Page } from '../core/api.models';

/** Mirrors backend onboarding DTOs (Phase 6). */
export type OnboardingStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

export interface ChecklistItem {
  label: string;
  done: boolean;
}

export interface Onboarding {
  id: number;
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  departmentName: string | null;
  applicationId: number | null;
  candidateName: string | null;
  jobTitle: string | null;
  joiningDate: string | null;
  status: OnboardingStatus;
  completionPercentage: number;
  checklist: ChecklistItem[];
}

/** A SELECTED application eligible for onboarding. */
export interface SelectableApplication {
  id: number;
  candidateName: string;
  jobTitle: string;
}

/** Data access for the onboarding module (Phase 6). */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly http = inject(HttpClient);

  list(): Observable<ApiResponse<Onboarding[]>> {
    return this.http
      .get<ApiResponse<Page<Onboarding>>>('/api/v1/onboardings', { params: { size: '100' } })
      .pipe(map((r) => ({ ...r, data: r.data.content })));
  }

  startFromApplication(applicationId: number, joiningDate: string | null): Observable<ApiResponse<Onboarding>> {
    return this.http.post<ApiResponse<Onboarding>>('/api/v1/onboardings/start-application', {
      applicationId,
      joiningDate,
    });
  }

  startForEmployee(employeeId: number, joiningDate: string | null): Observable<ApiResponse<Onboarding>> {
    return this.http.post<ApiResponse<Onboarding>>('/api/v1/onboardings/start-employee', {
      employeeId,
      joiningDate,
    });
  }

  toggleItem(onboardingId: number, itemIndex: number, done: boolean): Observable<ApiResponse<Onboarding>> {
    return this.http.patch<ApiResponse<Onboarding>>(`/api/v1/onboardings/${onboardingId}/checklist`, {
      itemIndex,
      done,
    });
  }

  /** SELECTED applications (for the start-from-pipeline dialog). */
  selectableApplications(): Observable<ApiResponse<{ id: number; candidateName: string; jobTitle: string }[]>> {
    return this.http.get<ApiResponse<{ id: number; candidateName: string; jobTitle: string }[]>>('/api/v1/applications');
  }
}
