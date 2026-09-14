import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { RecruitmentService } from './recruitment.service';
import { ApiResponse } from '../core/api.models';

describe('RecruitmentService', () => {
  let service: RecruitmentService;
  let http: HttpTestingController;

  const envelope = <T>(data: T): ApiResponse<T> => ({ success: true, message: 'OK', data });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(RecruitmentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists jobs from /api/v1/jobs and unwraps the page envelope (Phase 14)', () => {
    let result: unknown;
    service.jobs().subscribe((r) => (result = r));
    const req = http.expectOne((r) => r.url === '/api/v1/jobs' && r.params.get('size') === '100');
    req.flush(
      envelope({
        content: [{ id: 1, title: 'Backend Developer' }],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      }),
    );
    expect(result).toEqual({ success: true, message: 'OK', data: [{ id: 1, title: 'Backend Developer' }] });
  });

  it('fetches pipeline KPI counts from /api/v1/applications/counts', () => {
    let result: unknown;
    service.applicationCounts().subscribe((r) => (result = r));
    http.expectOne('/api/v1/applications/counts').flush(envelope({ APPLIED: 1, SCREENING: 2 }));
    expect(result).toEqual({ success: true, message: 'OK', data: { APPLIED: 1, SCREENING: 2 } });
  });

  it('sends PATCH with status+remarks for pipeline transitions', () => {
    service.transition(7, 'SCREENING', 'looks good').subscribe();
    const req = http.expectOne('/api/v1/applications/7/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'SCREENING', remarks: 'looks good' });
    req.flush(envelope(null));
  });

  it('creates interviews via POST /api/v1/interviews', () => {
    service
      .createInterview({ applicationId: 3, interviewerId: 1, interviewDate: '2026-09-20T10:00:00+05:30', mode: 'ONSITE' })
      .subscribe();
    const req = http.expectOne('/api/v1/interviews');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      applicationId: 3,
      interviewerId: 1,
      interviewDate: '2026-09-20T10:00:00+05:30',
      mode: 'ONSITE',
    });
    req.flush(envelope(null));
  });

  it('completes interviews via the /complete subresource', () => {
    service.completeInterview(5, { result: 'PASS', feedback: 'great' }).subscribe();
    const req = http.expectOne('/api/v1/interviews/5/complete');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ result: 'PASS', feedback: 'great' });
    req.flush(envelope(null));
  });
});
