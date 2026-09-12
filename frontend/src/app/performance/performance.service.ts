import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../core/api.models';

/** Mirrors backend performance module DTOs (Phase 10). */

export type ReviewStatus = 'DRAFT' | 'SUBMITTED' | 'ACKNOWLEDGED';

export interface PerformanceReview {
  id: number;
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  departmentName: string | null;
  reviewerId: number;
  reviewerName: string;
  reviewPeriod: string;
  rating: number | null;
  strengths: string | null;
  weaknesses: string | null;
  goals: string | null;
  comments: string | null;
  status: ReviewStatus;
}

export interface RatingBucket {
  rating: number;
  count: number;
}

export interface PerformanceSummary {
  totalReviews: number;
  draftCount: number;
  submittedCount: number;
  acknowledgedCount: number;
  averageRating: number | null;
  ratingDistribution: RatingBucket[];
}

export interface EmployeeOption {
  id: number;
  label: string;
}

/** Data access for the performance module (Phase 10). */
@Injectable({ providedIn: 'root' })
export class PerformanceService {
  private readonly http = inject(HttpClient);

  reviews(employeeId?: number): Observable<ApiResponse<PerformanceReview[]>> {
    const params = employeeId ? { employeeId: String(employeeId) } : undefined;
    return this.http.get<ApiResponse<PerformanceReview[]>>('/api/v1/performance/reviews', { params });
  }

  summary(): Observable<ApiResponse<PerformanceSummary>> {
    return this.http.get<ApiResponse<PerformanceSummary>>('/api/v1/performance/summary');
  }

  create(payload: {
    employeeId: number;
    reviewerId: number;
    reviewPeriod: string;
    goals: string | null;
  }): Observable<ApiResponse<PerformanceReview>> {
    return this.http.post<ApiResponse<PerformanceReview>>('/api/v1/performance/reviews', payload);
  }

  /** Edit a DRAFT review's narrative fields. */
  update(id: number, payload: {
    goals: string | null;
    strengths: string | null;
    weaknesses: string | null;
    comments: string | null;
  }): Observable<ApiResponse<PerformanceReview>> {
    return this.http.patch<ApiResponse<PerformanceReview>>(`/api/v1/performance/reviews/${id}`, payload);
  }

  /** Rate 1–5 and submit in one step. */
  rate(id: number, rating: number, comments: string | null): Observable<ApiResponse<PerformanceReview>> {
    return this.http.patch<ApiResponse<PerformanceReview>>(`/api/v1/performance/reviews/${id}/rate`, {
      rating,
      comments,
    });
  }

  acknowledge(id: number): Observable<ApiResponse<PerformanceReview>> {
    return this.http.patch<ApiResponse<PerformanceReview>>(`/api/v1/performance/reviews/${id}/acknowledge`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/performance/reviews/${id}`);
  }

  /** Shared /employees/options contract (Phase 4): { id, label }. */
  employeeOptions(): Observable<ApiResponse<EmployeeOption[]>> {
    return this.http.get<ApiResponse<EmployeeOption[]>>('/api/v1/employees/options');
  }
}