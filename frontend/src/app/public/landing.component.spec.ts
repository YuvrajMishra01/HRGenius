import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { LandingComponent } from './landing.component';

describe('LandingComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LandingComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([])),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  afterEach(() => localStorage.clear());

  it('renders the hero, trust row and primary CTAs', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Where your people');
    expect(el.textContent).toContain('Open HRGenius');
    expect(el.textContent).toContain('Employee Lifecycle');
    expect(el.textContent).toContain('Recruitment');
  });

  it('renders the static dashboard preview with demo values only', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Total Employees');
    expect(el.textContent).toContain('248');
    expect(el.textContent).toContain('₹24.8L');
    expect(el.textContent).toContain('Recruitment Pipeline');
  });

  it('renders the bento features, security promises and roles', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Employee Management');
    expect(el.textContent).toContain('JWT Authentication');
    expect(el.textContent).toContain('Role-Based Access');
    expect(el.textContent).toContain('ADMIN');
    expect(el.textContent).toContain('EMPLOYEE');
  });

  it('renders all four how-it-works steps and footer columns', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Set up your organization');
    expect(el.textContent).toContain('Analyze and report');
    expect(el.textContent).toContain('Workflows');
    expect(el.textContent).toContain(`© ${new Date().getFullYear()} HRGenius`);
  });

  it('does not issue any HTTP requests while rendering (public page is static)', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    expect(() => fixture.detectChanges()).not.toThrow();
  });
});
