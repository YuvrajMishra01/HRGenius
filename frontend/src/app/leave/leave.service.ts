import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiResponse, Page } from '../core/api.models';

/** Mirrors backend leave module DTOs (Phase 8). */

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface LeaveType {
  id: number;
  name: string;
  description: string | null;
  yearlyLimit: number;
  usageCount: number;
}

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  departmentName: string | null;
  leaveTypeId: number;
  leaveTypeName: string;
  startDate: string;
  endDate: string;
  workingDays: number;
  reason: string | null;
  status: LeaveStatus;
  approverEmail: string | null;
  createdAt: string;
}

export interface BalanceRow {
  leaveTypeId: number;
  leaveTypeName: string;
  yearlyLimit: number;
  usedDays: number;
  remainingDays: number;
}

export interface BalanceResponse {
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  year: number;
  balances: BalanceRow[];
}

export interface LeaveSummary {
  pendingCount: number;
  approvedThisYear: number;
  rejectedCount: number;
  approvalRate: number;
}

export interface EmployeeOption {
  id: number;
  label: string;
}

/** Data access for the leave module (Phase 8). */
@Injectable({ providedIn: 'root' })
export class LeaveService {
  private readonly http = inject(HttpClient);

  // ------------------------------------------------------------ types

  types(): Observable<ApiResponse<LeaveType[]>> {
    return this.http.get<ApiResponse<LeaveType[]>>('/api/v1/leave/types');
  }

  createType(payload: { name: string; description: string | null; yearlyLimit: number }):
      Observable<ApiResponse<LeaveType>> {
    return this.http.post<ApiResponse<LeaveType>>('/api/v1/leave/types', payload);
  }

  updateType(id: number, payload: { name: string; description: string | null; yearlyLimit: number }):
      Observable<ApiResponse<LeaveType>> {
    return this.http.patch<ApiResponse<LeaveType>>(`/api/v1/leave/types/${id}`, payload);
  }

  deleteType(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/leave/types/${id}`);
  }

  // ------------------------------------------------------------ requests

  requests(status?: LeaveStatus): Observable<ApiResponse<LeaveRequest[]>> {
    const params: Record<string, string> = { size: '100' };
    if (status) {
      params['status'] = status;
    }
    return this.http
      .get<ApiResponse<Page<LeaveRequest>>>('/api/v1/leave/requests', { params })
      .pipe(map((r) => ({ ...r, data: r.data.content })));
  }

  createRequest(payload: {
    employeeId: number;
    leaveTypeId: number;
    startDate: string;
    endDate: string;
    reason: string | null;
  }): Observable<ApiResponse<LeaveRequest>> {
    return this.http.post<ApiResponse<LeaveRequest>>('/api/v1/leave/requests', payload);
  }

  approve(id: number): Observable<ApiResponse<LeaveRequest>> {
    return this.http.patch<ApiResponse<LeaveRequest>>(`/api/v1/leave/requests/${id}/approve`, {});
  }

  reject(id: number): Observable<ApiResponse<LeaveRequest>> {
    return this.http.patch<ApiResponse<LeaveRequest>>(`/api/v1/leave/requests/${id}/reject`, {});
  }

  cancel(id: number): Observable<void> {
    return this.http.patch<void>(`/api/v1/leave/requests/${id}/cancel`, {});
  }

  // -------------------------------------------------- balances & summary

  balances(employeeId: number, year?: number): Observable<ApiResponse<BalanceResponse>> {
    const params = year ? { year: String(year) } : undefined;
    return this.http.get<ApiResponse<BalanceResponse>>(`/api/v1/leave/balances/${employeeId}`, { params });
  }

  summary(): Observable<ApiResponse<LeaveSummary>> {
    return this.http.get<ApiResponse<LeaveSummary>>('/api/v1/leave/summary');
  }

  /** Shared /employees/options contract (Phase 4): { id, label }. */
  employeeOptions(): Observable<ApiResponse<EmployeeOption[]>> {
    return this.http.get<ApiResponse<EmployeeOption[]>>('/api/v1/employees/options');
  }
}
