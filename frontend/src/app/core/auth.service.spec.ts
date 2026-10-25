import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';

import { AuthService, TOKEN_KEY, USER_KEY } from './auth.service';
import { authInterceptor } from './interceptors';

/** Builds a JWT-looking token with the given `exp` epoch seconds. */
function fakeToken(expSeconds: number): string {
  const payload = btoa(JSON.stringify({ sub: 'a@b.c', exp: expSeconds }).replace(/-/g, '+').replace(/_/g, '/'));
  return `header.${payload}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      // Real authInterceptor so the tests prove the Bearer header wiring
      // exactly as the app configures it (app.config.ts).
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  afterEach(() => {
    localStorage.clear();
    httpMock.verify();
  });

  it('login stores token + user and exposes an authenticated session', () => {
    service.login('admin@hrgenius.local', 'Admin@123').subscribe();

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'admin@hrgenius.local', password: 'Admin@123' });
    req.flush({
      success: true,
      message: 'Login successful',
      data: {
        token: fakeToken(Math.floor(Date.now() / 1000) + 3600),
        tokenType: 'Bearer',
        expiresInMinutes: 60,
        userId: 1,
        email: 'admin@hrgenius.local',
        fullName: 'System Administrator',
        role: 'ADMIN',
      },
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.user()?.role).toBe('ADMIN');
    expect(localStorage.getItem(TOKEN_KEY)).toBeTruthy();
    expect(JSON.parse(localStorage.getItem(USER_KEY)!).fullName).toBe('System Administrator');
  });

  it('isAuthenticated is false without a stored session', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.user()).toBeNull();
  });

  it('isAuthenticated is false when the stored token is expired', () => {
    localStorage.setItem(TOKEN_KEY, fakeToken(Math.floor(Date.now() / 1000) - 10));
    localStorage.setItem(USER_KEY, JSON.stringify({ userId: 1, email: 'a@b.c', fullName: 'A', role: 'HR' }));
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('logout sends the Bearer header while the token is still stored, then clears the session', () => {
    const token = fakeToken(Math.floor(Date.now() / 1000) + 3600);
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify({ userId: 2, email: 'e@hrgenius.local', fullName: 'E', role: 'EMPLOYEE' }));

    service.logout();

    // While the request is in flight the token must still be present —
    // the authInterceptor reads localStorage at request time.
    expect(localStorage.getItem(TOKEN_KEY)).not.toBeNull();

    const req = httpMock.expectOne('/api/v1/auth/logout');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    req.flush({ success: true, message: 'Logged out' });

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(USER_KEY)).toBeNull();
    expect(service.user()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logout clears the session and redirects even when the API call fails', () => {
    localStorage.setItem(TOKEN_KEY, fakeToken(Math.floor(Date.now() / 1000) + 3600));
    localStorage.setItem(USER_KEY, JSON.stringify({ userId: 2, email: 'e@hrgenius.local', fullName: 'E', role: 'EMPLOYEE' }));

    service.logout();

    const req = httpMock.expectOne('/api/v1/auth/logout');
    req.flush({ timestamp: '', status: 500, message: 'Server error', path: '/api/v1/auth/logout', errors: {} }, {
      status: 500,
      statusText: 'Server Error',
    });

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(USER_KEY)).toBeNull();
    expect(service.user()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logout without a token skips the server call but still clears and redirects', () => {
    service.logout();

    httpMock.expectNone('/api/v1/auth/logout');
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(service.user()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
