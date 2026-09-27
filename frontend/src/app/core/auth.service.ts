import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { finalize, map, Observable, tap } from 'rxjs';

import { ApiResponse } from './api.models';

export type UserRole = 'ADMIN' | 'HR' | 'MANAGER' | 'EMPLOYEE';

/** Authenticated user info exposed to the UI. */
export interface AuthUser {
  userId: number;
  email: string;
  fullName: string;
  role: UserRole;
}

/** `data` payload of POST /api/v1/auth/login. */
export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  userId: number;
  email: string;
  fullName: string;
  role: UserRole;
}

/** localStorage keys — also read directly by the HTTP interceptor (no DI cycle). */
export const TOKEN_KEY = 'hrgenius.token';
export const USER_KEY = 'hrgenius.user';

/**
 * Client-side auth session:
 * - login() stores the JWT + user in localStorage and signals;
 * - isAuthenticated derives from token presence AND expiry (exp claim);
 * - logout() invalidates server-side FIRST (the auth interceptor needs the
 *   token still in localStorage to attach the Bearer header), then clears
 *   the local session — on success or failure alike.
 *
 * The token survives page reloads; /me could re-hydrate it on demand later.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly baseUrl = '/api/v1/auth';

  private readonly userSignal = signal<AuthUser | null>(this.readStoredUser());
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.userSignal() !== null && !this.isTokenExpired());

  login(email: string, password: string): Observable<AuthUser> {
    return this.http
      .post<ApiResponse<LoginResponse>>(`${this.baseUrl}/login`, { email, password })
      .pipe(
        tap((res) => this.storeSession(res.data)),
        map((res) => ({
          userId: res.data.userId,
          email: res.data.email,
          fullName: res.data.fullName,
          role: res.data.role,
        })),
      );
  }

  /**
   * Explicit user logout: the authenticated POST /logout must run while the
   * token is still in localStorage so the auth interceptor attaches the
   * Bearer header and the backend can bump the token version. The local
   * session is cleared once the request settles (success OR failure), so the
   * user is never trapped in the app.
   */
  logout(): void {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      // Nothing to invalidate server-side: just clear and redirect.
      this.clearSession();
      this.router.navigate(['/login']);
      return;
    }
    this.http
      .post(`${this.baseUrl}/logout`, null)
      .pipe(
        finalize(() => {
          this.clearSession();
          this.router.navigate(['/login']);
        }),
      )
      .subscribe({ error: () => undefined });
  }

  /** Session invalid (expired/rejected by server): clear without server call. */
  sessionExpired(): void {
    this.clearSession();
    this.router.navigate(['/login']);
  }

  private storeSession(data: LoginResponse): void {
    localStorage.setItem(TOKEN_KEY, data.token);
    const user: AuthUser = {
      userId: data.userId,
      email: data.email,
      fullName: data.fullName,
      role: data.role,
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.userSignal.set(user);
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.userSignal.set(null);
  }

  private readStoredUser(): AuthUser | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as AuthUser) : null;
    } catch {
      return null;
    }
  }

  /** Decodes the JWT exp claim (client-side hint only; server is authoritative). */
  private isTokenExpired(): boolean {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      return true;
    }
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }
}
