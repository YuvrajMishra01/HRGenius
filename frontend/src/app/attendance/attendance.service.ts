import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../core/api.models';

/** Mirrors backend attendance DTOs (Phase 7). */
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'HALF_DAY' | 'LEAVE' | 'HOLIDAY';

export interface AttendanceRecord {
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  departmentName: string | null;
  date: string;
  status: AttendanceStatus;
  checkIn: string | null;
  checkOut: string | null;
  workingHours: number | null;
}

export interface DaySummary {
  present: number;
  halfDay: number;
  absent: number;
  leave: number;
  holiday: number;
  total: number;
}

export interface TodayResponse {
  date: string;
  summary: DaySummary;
  records: AttendanceRecord[];
}

export interface MonthRow {
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  departmentName: string | null;
  present: number;
  halfDay: number;
  absent: number;
  leave: number;
  holiday: number;
  totalRecords: number;
  workingHours: number;
  attendancePercent: number;
  /** Day-of-month → status; days without a record have no key. */
  days: Record<string, AttendanceStatus>;
}

export interface MonthResponse {
  year: number;
  month: number;
  rows: MonthRow[];
}

/** Data access for the attendance module (Phase 7). */
@Injectable({ providedIn: 'root' })
export class AttendanceService {
  private readonly http = inject(HttpClient);

  today(): Observable<ApiResponse<TodayResponse>> {
    return this.http.get<ApiResponse<TodayResponse>>('/api/v1/attendance/today');
  }

  month(year?: number, month?: number): Observable<ApiResponse<MonthResponse>> {
    const params: Record<string, number> = {};
    if (year != null) params['year'] = year;
    if (month != null) params['month'] = month;
    return this.http.get<ApiResponse<MonthResponse>>('/api/v1/attendance/month', { params });
  }

  checkIn(employeeId: number): Observable<ApiResponse<AttendanceRecord>> {
    return this.http.post<ApiResponse<AttendanceRecord>>(
      `/api/v1/attendance/check-in?employeeId=${employeeId}`, {});
  }

  checkOut(employeeId: number): Observable<ApiResponse<AttendanceRecord>> {
    return this.http.post<ApiResponse<AttendanceRecord>>(
      `/api/v1/attendance/check-out?employeeId=${employeeId}`, {});
  }

  mark(request: {
    employeeId: number;
    date: string;
    status: AttendanceStatus;
  }): Observable<ApiResponse<AttendanceRecord>> {
    return this.http.post<ApiResponse<AttendanceRecord>>('/api/v1/attendance/mark', request);
  }

  /** Employee options for the check-in/marking pickers. */
  employeeOptions(): Observable<ApiResponse<{ id: number; label: string }[]>> {
    return this.http.get<ApiResponse<{ id: number; label: string }[]>>('/api/v1/employees/options');
  }
}
