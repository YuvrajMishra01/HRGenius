import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../core/api.models';

/** Mirrors backend payroll module DTOs (Phase 9). */

export type PayrollStatus = 'DRAFT' | 'PROCESSED' | 'PAID';

export interface RunResponse {
  year: number;
  month: number;
  created: number;
  skipped: number;
  periodPayslips: number;
  periodTotalNet: number;
}

export interface PayrollRow {
  id: number;
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  departmentName: string | null;
  payYear: number;
  payMonth: number;
  basicSalary: number;
  allowances: number;
  deductions: number;
  tax: number;
  netSalary: number;
  status: PayrollStatus;
}

export interface PeriodSummary {
  year: number;
  month: number;
  payslipCount: number;
  totalGross: number;
  totalNet: number;
  draftCount: number;
  processedCount: number;
  paidCount: number;
}

export interface PeriodResponse {
  summary: PeriodSummary;
  payslips: PayrollRow[];
}

export interface PeriodRow {
  year: number;
  month: number;
  payslipCount: number;
  totalNet: number;
}

export interface PeriodsResponse {
  periods: PeriodRow[];
  generatedAt: string;
}

/** Data access for the payroll module (Phase 9). */
@Injectable({ providedIn: 'root' })
export class PayrollService {
  private readonly http = inject(HttpClient);

  /** Generate DRAFT payslips for a period (existing rows are skipped). */
  run(year: number, month: number): Observable<ApiResponse<RunResponse>> {
    return this.http.post<ApiResponse<RunResponse>>('/api/v1/payrolls/run', { year, month });
  }

  /** All periods with payroll data, newest first. */
  periods(): Observable<ApiResponse<PeriodsResponse>> {
    return this.http.get<ApiResponse<PeriodsResponse>>('/api/v1/payrolls');
  }

  /** One period: summary + payslip rows. */
  period(year: number, month: number): Observable<ApiResponse<PeriodResponse>> {
    return this.http.get<ApiResponse<PeriodResponse>>(`/api/v1/payrolls/${year}/${month}`);
  }

  /** Adjust components; net is recomputed server-side. */
  updateComponents(id: number, payload: {
    basicSalary: number;
    allowances: number;
    deductions: number;
    tax: number;
  }): Observable<ApiResponse<PayrollRow>> {
    return this.http.patch<ApiResponse<PayrollRow>>(`/api/v1/payrolls/${id}/components`, payload);
  }

  process(id: number): Observable<ApiResponse<PayrollRow>> {
    return this.http.patch<ApiResponse<PayrollRow>>(`/api/v1/payrolls/${id}/process`, {});
  }

  markPaid(id: number): Observable<ApiResponse<PayrollRow>> {
    return this.http.patch<ApiResponse<PayrollRow>>(`/api/v1/payrolls/${id}/pay`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/payrolls/${id}`);
  }
}
