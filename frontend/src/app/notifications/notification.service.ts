import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse, Page } from '../core/api.models';

/** Mirrors backend notification DTOs (Phase 12). */
export interface Notification {
  id: number;
  userId: number;
  title: string;
  message: string | null;
  type: string | null;
  read: boolean;
  createdAt: string | null;
}

export interface UnreadResponse {
  unread: number;
}

/** Data access + shared unread-badge state (Phase 12). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/notifications';

  /** Shared between the toolbar bell and the notifications page. */
  readonly badge = signal(0);

  /**
   * Paginated feed (Phase 17): the backend pages and filters in SQL, so the
   * feed scales past any client clamp. Pass unreadOnly to use the DB-side
   * `unread` filter.
   */
  listPage(page: number, size: number, unreadOnly: boolean): Observable<ApiResponse<Page<Notification>>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (unreadOnly) {
      params['unread'] = 'true';
    }
    return this.http.get<ApiResponse<Page<Notification>>>(this.baseUrl, { params });
  }

  unread(): Observable<ApiResponse<UnreadResponse>> {
    return this.http.get<ApiResponse<UnreadResponse>>(`${this.baseUrl}/unread`);
  }

  markRead(id: number): Observable<ApiResponse<Notification>> {
    return this.http.patch<ApiResponse<Notification>>(`${this.baseUrl}/${id}/read`, null);
  }

  markAllRead(): Observable<ApiResponse<UnreadResponse>> {
    return this.http.patch<ApiResponse<UnreadResponse>>(`${this.baseUrl}/read-all`, null);
  }
}
