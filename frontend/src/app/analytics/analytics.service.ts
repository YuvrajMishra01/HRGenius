import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

import { ApiResponse } from '../core/api.models';

/** Mirrors backend AnalyticsDto (Phase 13). */
export interface NameCount {
  name: string;
  count: number;
}
export interface TypeCount {
  type: string;
  count: number;
}
export interface StatusCount {
  status: string;
  count: number;
}
export interface TenureBucket {
  label: string;
  count: number;
}

export interface Workforce {
  active: number;
  terminated: number;
  avgTenureYears: number;
  byDepartment: NameCount[];
  byEmploymentType: TypeCount[];
  byTenureBucket: TenureBucket[];
}

export interface JobFunnel {
  jobId: number;
  title: string;
  status: string;
  applications: number;
  active: number;
  selected: number;
  rejected: number;
}
export interface Funnel {
  totalApplications: number;
  perJob: JobFunnel[];
}

export interface Interviews {
  completed: number;
  scheduled: number;
  cancelled: number;
  pass: number;
  fail: number;
  onHold: number;
  passRate: number;
}

export interface LeaveAnalytics {
  year: number;
  byType: NameCount[];
  byStatus: StatusCount[];
}

export interface PeriodNet {
  year: number;
  month: number;
  payslips: number;
  totalNet: number;
}

export interface RatingCount {
  rating: number;
  count: number;
}
export interface Performance {
  totalReviews: number;
  averageRating: number;
  byStatus: StatusCount[];
  byRating: RatingCount[];
}

/** All sections for the page, loaded in one round trip. */
export interface AnalyticsData {
  workforce: Workforce;
  funnel: Funnel;
  interviews: Interviews;
  leave: LeaveAnalytics;
  payrollTrend: PeriodNet[];
  performance: Performance;
}

/** Data access for the analytics page (Phase 13). */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/analytics';

  /** Loads every section in parallel and unwraps the envelopes. */
  loadAll(): Observable<AnalyticsData> {
    return forkJoin({
      workforce: this.http.get<ApiResponse<Workforce>>(`${this.baseUrl}/workforce`).pipe(map((r) => r.data)),
      funnel: this.http.get<ApiResponse<Funnel>>(`${this.baseUrl}/funnel`).pipe(map((r) => r.data)),
      interviews: this.http.get<ApiResponse<Interviews>>(`${this.baseUrl}/interviews`).pipe(map((r) => r.data)),
      leave: this.http.get<ApiResponse<LeaveAnalytics>>(`${this.baseUrl}/leave`).pipe(map((r) => r.data)),
      payrollTrend: this.http
        .get<ApiResponse<{ periods: PeriodNet[] }>>(`${this.baseUrl}/payroll-trend`)
        .pipe(map((r) => r.data.periods)),
      performance: this.http.get<ApiResponse<Performance>>(`${this.baseUrl}/performance`).pipe(map((r) => r.data)),
    });
  }
}
