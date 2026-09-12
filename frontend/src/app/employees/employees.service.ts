import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../core/api.models';
import { Employee, EmployeeRequest, Option, PageResponse } from './employees.models';

export interface EmployeeQuery {
  search?: string;
  departmentId?: number | null;
  status?: string;
  employmentType?: string;
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
}

/** Data access for employee management (Phase 3). */
@Injectable({ providedIn: 'root' })
export class EmployeesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/employees';

  list(query: EmployeeQuery): Observable<ApiResponse<PageResponse<Employee>>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('sortDir', query.sortDir);
    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.departmentId != null) {
      params = params.set('departmentId', query.departmentId);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.employmentType) {
      params = params.set('employmentType', query.employmentType);
    }
    return this.http.get<ApiResponse<PageResponse<Employee>>>(this.baseUrl, { params });
  }

  get(id: number): Observable<ApiResponse<Employee>> {
    return this.http.get<ApiResponse<Employee>>(`${this.baseUrl}/${id}`);
  }

  create(request: EmployeeRequest): Observable<ApiResponse<Employee>> {
    return this.http.post<ApiResponse<Employee>>(this.baseUrl, request);
  }

  update(id: number, request: EmployeeRequest): Observable<ApiResponse<Employee>> {
    return this.http.put<ApiResponse<Employee>>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${id}`);
  }

  departments(): Observable<ApiResponse<Option[]>> {
    return this.http.get<ApiResponse<Option[]>>('/api/v1/departments');
  }

  /** id+label list of all employees, for manager pickers. */
  managerOptions(): Observable<ApiResponse<{ id: number; label: string }[]>> {
    return this.http.get<ApiResponse<{ id: number; label: string }[]>>('/api/v1/employees/options');
  }

  designations(departmentId?: number | null): Observable<ApiResponse<Option[]>> {
    const params = departmentId != null ? new HttpParams().set('departmentId', departmentId) : undefined;
    return this.http.get<ApiResponse<Option[]>>('/api/v1/designations', { params });
  }
}
