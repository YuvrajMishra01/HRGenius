import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { AuthService } from './auth.service';
import { authGuard, guestGuard } from './guards';

const route = {} as ActivatedRouteSnapshot;
const state = (url: string) => ({ url }) as RouterStateSnapshot;

function seedSession(expired = false): void {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + (expired ? -10 : 3600) }));
  localStorage.setItem('hrgenius.token', `h.${payload}.s`);
  localStorage.setItem('hrgenius.user', JSON.stringify({ userId: 1, email: 'a@b.c', fullName: 'A', role: 'HR' }));
}

describe('authGuard', () => {
  beforeEach(() => localStorage.clear());

  it('redirects unauthenticated users to /login with a returnUrl', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const result = TestBed.runInInjectionContext(() => authGuard(route, state('/dashboard')));
    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toContain('/login');
    expect((result as UrlTree).toString()).toContain('returnUrl=%2Fdashboard');
  });

  it('allows authenticated users through', () => {
    seedSession(false);
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const result = TestBed.runInInjectionContext(() => authGuard(route, state('/dashboard')));
    expect(result).toBeTrue();
  });
});

describe('guestGuard', () => {
  beforeEach(() => localStorage.clear());

  it('allows anonymous users to see the login page', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const result = TestBed.runInInjectionContext(() => guestGuard(route, state('/login')));
    expect(result).toBeTrue();
  });

  it('redirects logged-in users to /dashboard', () => {
    seedSession(false);
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const result = TestBed.runInInjectionContext(() => guestGuard(route, state('/login')));
    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/dashboard');
  });
});
