import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../core/api.models';

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

  list(): Observable<ApiResponse<Notification[]>> {
    return this.http.get<ApiResponse<Notification[]>>(this.baseUrl);
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
