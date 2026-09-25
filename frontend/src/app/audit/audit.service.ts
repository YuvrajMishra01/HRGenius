import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse, Page } from '../core/api.models';

/** Mirrors the backend AuditLogResponse DTO (Phase 18). */
export interface AuditEntry {
  id: number;
  actorId: number | null;
  actorEmail: string | null;
  actorName: string | null;
  actorRole: string | null;
  action: string;
  entityType: string;
  entityId: number | null;
  entityLabel: string | null;
  details: string | null;
  createdAt: string | null;
}

export interface AuditFilter {
  action?: string;
  entityType?: string;
  actorId?: number;
  from?: string;
  to?: string;
  search?: string;
  page?: number;
  size?: number;
}

/** Data access for the HR-facing audit log (Phase 18). ADMIN/HR only. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/audit';

  /** Paged, filtered, searched audit feed — all filtering happens DB-side. */
  list(filter: AuditFilter): Observable<ApiResponse<Page<AuditEntry>>> {
    return this.http.get<ApiResponse<Page<AuditEntry>>>(
      this.baseUrl,
      { params: this.filterParams(filter) },
    );
  }

  /**
   * Shared filter→query-param mapping for the feed and the export, so the
   * two endpoints can never drift apart. Set `omitPaging` for the export:
   * it is server-capped and paged endpoints do not apply.
   */
  private filterParams(filter: AuditFilter, omitPaging = false): Record<string, string> {
    const params: Record<string, string> = {};
    const entries: [string, string | number | undefined][] = [
      ['action', filter.action],
      ['entityType', filter.entityType],
      ['actorId', filter.actorId],
      ['from', filter.from],
      ['to', filter.to],
      ['search', filter.search],
      ['page', omitPaging ? undefined : filter.page],
      ['size', omitPaging ? undefined : filter.size],
    ];
    for (const [key, value] of entries) {
      if (value !== undefined && value !== null && value !== '') {
        params[key] = String(value);
      }
    }
    return params;
  }

  /** Distinct entity families in the trail — feeds the filter dropdown. */
  entityTypes(): Observable<ApiResponse<string[]>> {
    return this.http.get<ApiResponse<string[]>>(`${this.baseUrl}/entity-types`);
  }

  /**
   * CSV export (Phase 19) — raw bytes, outside the ApiResponse envelope.
   * Consumed through the shared ReportService blob downloader so the
   * browser gets a real "Save file" with the server's suggested name.
   */
  exportCsv(filter: AuditFilter): string {
    const params = this.filterParams(filter, true);
    const query = new URLSearchParams(params).toString();
    return `${this.baseUrl}/export.csv${query ? '?' + query : ''}`;
  }
}
