import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';

import { AuthService, TOKEN_KEY, USER_KEY } from './auth.service';

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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
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

  it('logout clears the session and invalidates the token server-side', () => {
    localStorage.setItem(TOKEN_KEY, fakeToken(Math.floor(Date.now() / 1000) + 3600));
    localStorage.setItem(USER_KEY, JSON.stringify({ userId: 2, email: 'e@hrgenius.local', fullName: 'E', role: 'EMPLOYEE' }));

    service.logout();

    const req = httpMock.expectOne('/api/v1/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, message: 'Logged out' });

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(service.user()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
