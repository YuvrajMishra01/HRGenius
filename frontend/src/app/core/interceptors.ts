import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

import { ApiError } from './api.models';

/** Placeholder token store — Phase 1 replaces this with the real auth session. */
const TOKEN_KEY = 'hrgenius.token';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

/** Normalizes backend errors and surfaces a readable toast. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
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
