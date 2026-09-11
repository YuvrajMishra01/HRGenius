import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';

import { authInterceptor, errorInterceptor } from './interceptors';

describe('auth + error interceptors', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;
  let snackBar: MatSnackBar;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    snackBar = TestBed.inject(MatSnackBar);
    spyOn(router, 'navigate');
    spyOn(snackBar, 'open');
  });

  afterEach(() => {
    localStorage.clear();
    httpMock.verify();
  });

  it('attaches the Bearer token when a session exists', () => {
    localStorage.setItem('hrgenius.token', 'abc.token.123');

    http.get('/api/v1/auth/me').subscribe();
    const req = httpMock.expectOne('/api/v1/auth/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc.token.123');
    req.flush({ success: true, message: 'OK', data: null });
  });

  it('sends no Authorization header without a session', () => {
    http.get('/api/v1/auth/me').subscribe();
    const req = httpMock.expectOne('/api/v1/auth/me');
    expect(req.request.headers.get('Authorization')).toBeNull();
    req.flush({ success: true, message: 'OK', data: null });
  });

  it('a 401 clears the session and redirects to /login', () => {
    localStorage.setItem('hrgenius.token', 'stale.token.old');
    localStorage.setItem('hrgenius.user', JSON.stringify({ email: 'a@b.c' }));

    http.get('/api/v1/auth/me').subscribe({ error: () => undefined });
    const req = httpMock.expectOne('/api/v1/auth/me');
    req.flush(
      { timestamp: '', status: 401, message: 'Authentication required', path: '/api/v1/auth/me', errors: {} },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(localStorage.getItem('hrgenius.token')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    expect(snackBar.open).toHaveBeenCalled();
  });

  it('login 401 failures do not trigger the redirect loop', () => {
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    const req = httpMock.expectOne('/api/v1/auth/login');
    req.flush(
      { timestamp: '', status: 401, message: 'Invalid email or password', path: '/api/v1/auth/login', errors: {} },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(router.navigate).not.toHaveBeenCalled();
    // But the invalid-credentials toast still fires.
    expect(snackBar.open).toHaveBeenCalledWith('Invalid email or password', 'Dismiss', jasmine.any(Object));
  });
});
