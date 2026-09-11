import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ApiService } from './api.service';
import { HealthInfo } from './api.models';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET should target /api/v1 and unwrap the ApiResponse envelope', () => {
    const health: HealthInfo = {
      application: 'HRGenius API',
      status: 'UP',
      database: 'UP',
      timestamp: '2026-09-11T00:00:00Z',
    };

    service.get<HealthInfo>('/health').subscribe((res) => {
      expect(res.success).toBeTrue();
      expect(res.data.status).toBe('UP');
    });

    const req = httpMock.expectOne('/api/v1/health');
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, message: 'OK', data: health });
  });

  it('POST should send a JSON body to /api/v1', () => {
    service.post('/auth/login', { email: 'a@b.c' }).subscribe();
    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.c' });
    req.flush({ success: true, message: 'OK', data: null });
  });
});
