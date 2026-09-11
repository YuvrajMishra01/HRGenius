import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

import { ApiError } from './api.models';
import { TOKEN_KEY } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

/**
 * Normalizes backend errors and surfaces a readable toast.
 * A 401 anywhere (invalid/expired token, logged out server-side) clears
 * the session and redirects to /login exactly once per failure.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  const router = inject(Router);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        if (err.status === 401 && !req.url.includes('/auth/login')) {
          const raw = localStorage.getItem(USER_CLEAR_KEY);
          if (raw) {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_CLEAR_KEY);
            snackBar.open('Your session has expired. Please sign in again.', 'Dismiss', { duration: 5000 });
            router.navigate(['/login']);
          }
        }
        const apiError = err.error as ApiError | null;
        const message =
          apiError?.message ??
          (err.status === 0 ? 'Cannot reach the server' : `Request failed (${err.status})`);
        snackBar.open(message, 'Dismiss', { duration: 5000 });
      }
      return throwError(() => err);
    }),
  );
};

/** Same storage key as AuthService.USER_KEY, duplicated to avoid an import cycle. */
const USER_CLEAR_KEY = 'hrgenius.user';
