import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiResponse, Page } from '../core/api.models';

/** Mirrors backend document module DTOs (Phase 11). */

export type DocumentType =
  | 'RESUME'
  | 'OFFER_LETTER'
  | 'ID_PROOF'
  | 'CERTIFICATE'
  | 'EXPERIENCE_LETTER'
  | 'OTHER';

export interface EmployeeDocument {
  id: number;
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  documentType: DocumentType;
  fileName: string;
  fileSize: number | null;
  uploadedAt: string | null;
}

export interface EmployeeOption {
  id: number;
  label: string;
}

export const DOCUMENT_TYPES: DocumentType[] = [
  'RESUME',
  'OFFER_LETTER',
  'ID_PROOF',
  'CERTIFICATE',
  'EXPERIENCE_LETTER',
  'OTHER',
];

/** Data access for the documents module (Phase 11). */
@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly http = inject(HttpClient);

  list(employeeId: number): Observable<ApiResponse<EmployeeDocument[]>> {
    return this.http
      .get<ApiResponse<Page<EmployeeDocument>>>('/api/v1/documents', {
        params: { employeeId: String(employeeId), size: '100' },
      })
      .pipe(map((r) => ({ ...r, data: r.data.content })));
  }

  upload(employeeId: number, documentType: DocumentType, file: File): Observable<ApiResponse<EmployeeDocument>> {
    const form = new FormData();
    form.append('employeeId', String(employeeId));
    form.append('documentType', documentType);
    form.append('file', file);
    return this.http.post<ApiResponse<EmployeeDocument>>('/api/v1/documents', form);
  }

  /** Streams the file with the original name via Content-Disposition (auth needed). */
  download(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`/api/v1/documents/${id}/download`, {
      responseType: 'blob',
      observe: 'response',
    });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/documents/${id}`);
  }

  /** Shared /employees/options contract (Phase 4): { id, label }. */
  employeeOptions(): Observable<ApiResponse<EmployeeOption[]>> {
    return this.http.get<ApiResponse<EmployeeOption[]>>('/api/v1/employees/options');
  }
}
