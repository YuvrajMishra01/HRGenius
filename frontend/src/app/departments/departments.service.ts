import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../core/api.models';

export interface Department {
  id: number;
  name: string;
  description: string | null;
  managerId: number | null;
  managerName: string | null;
  employeeCount: number;
}

export interface Designation {
  id: number;
  title: string;
  description: string | null;
  departmentId: number;
  departmentName: string | null;
  employeeCount: number;
}

export interface DepartmentRequest {
  name: string;
  description: string | null;
  managerId: number | null;
}

export interface DesignationRequest {
  title: string;
  description: string | null;
  departmentId: number;
}

export interface EmployeeOption {
  id: number;
  label: string;
}

/** Data access for department & designation management (Phase 4). */
@Injectable({ providedIn: 'root' })
export class DepartmentsService {
  private readonly http = inject(HttpClient);

  list(): Observable<ApiResponse<Department[]>> {
    return this.http.get<ApiResponse<Department[]>>('/api/v1/departments');
  }

  create(request: DepartmentRequest): Observable<ApiResponse<Department>> {
    return this.http.post<ApiResponse<Department>>('/api/v1/departments', request);
  }

  update(id: number, request: DepartmentRequest): Observable<ApiResponse<Department>> {
    return this.http.put<ApiResponse<Department>>(`/api/v1/departments/${id}`, request);
  }

  delete(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`/api/v1/departments/${id}`);
  }

  designations(departmentId?: number | null): Observable<ApiResponse<Designation[]>> {
    const params = departmentId != null ? new HttpParams().set('departmentId', departmentId) : undefined;
    return this.http.get<ApiResponse<Designation[]>>('/api/v1/designations', { params });
  }

  createDesignation(request: DesignationRequest): Observable<ApiResponse<Designation>> {
    return this.http.post<ApiResponse<Designation>>('/api/v1/designations', request);
  }

  updateDesignation(id: number, request: DesignationRequest): Observable<ApiResponse<Designation>> {
    return this.http.put<ApiResponse<Designation>>(`/api/v1/designations/${id}`, request);
  }

  deleteDesignation(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`/api/v1/designations/${id}`);
  }

  /** For manager pickers (also used by the employee dialog). */
  employeeOptions(): Observable<ApiResponse<EmployeeOption[]>> {
    return this.http.get<ApiResponse<EmployeeOption[]>>('/api/v1/employees/options');
  }
}
