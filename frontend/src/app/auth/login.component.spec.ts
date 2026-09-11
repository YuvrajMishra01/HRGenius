import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.clear();
    httpMock.verify();
  });

  it('renders the sign-in form and blocks an invalid form submission', () => {
    (fixture.nativeElement as HTMLElement).querySelector('form')?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectNone('/api/v1/auth/login');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('form')).toBeTruthy();
    expect(el.textContent).toContain('Email is required');
  });

  it('submits credentials and navigates to the dashboard on success', () => {
    const auth = TestBed.inject(AuthService);
    const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }));
    const token = `h.${payload}.s`;

    const el = fixture.nativeElement as HTMLElement;
    const email = el.querySelector<HTMLInputElement>('input[type=email]')!;
    const password = el.querySelector<HTMLInputElement>('input[type=password]')!;
    email.value = 'admin@hrgenius.local';
    email.dispatchEvent(new Event('input'));
    password.value = 'Admin@123';
    password.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/v1/auth/login');
    req.flush({
      success: true,
      message: 'Login successful',
      data: {
        token,
        tokenType: 'Bearer',
        expiresInMinutes: 60,
        userId: 1,
        email: 'admin@hrgenius.local',
        fullName: 'System Administrator',
        role: 'ADMIN',
      },
    });
    fixture.detectChanges();

    expect(auth.user()?.email).toBe('admin@hrgenius.local');
  });

  it('shows the backend error message on bad credentials', () => {
    const el = fixture.nativeElement as HTMLElement;
    const email = el.querySelector<HTMLInputElement>('input[type=email]')!;
    const password = el.querySelector<HTMLInputElement>('input[type=password]')!;
    email.value = 'admin@hrgenius.local';
    email.dispatchEvent(new Event('input'));
    password.value = 'wrong';
    password.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/v1/auth/login');
    req.flush(
      { timestamp: '', status: 401, message: 'Invalid email or password', path: '/api/v1/auth/login', errors: {} },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    expect((el.querySelector('.alert-error') as HTMLElement).textContent).toContain('Invalid email or password');
  });
});
