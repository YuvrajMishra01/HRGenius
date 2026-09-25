import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuditService } from './audit.service';

describe('AuditService', () => {
  let service: AuditService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() sends only the set filters plus paging', () => {
    service
      .list({
        action: 'LEAVE_REQUEST_APPROVED',
        entityType: undefined,
        from: '2026-09-01',
        to: undefined,
        search: 'kavya',
        page: 1,
        size: 30,
      })
      .subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/audit');
    const params = req.request.params;
    expect(params.get('action')).toBe('LEAVE_REQUEST_APPROVED');
    expect(params.has('entityType')).toBeFalse();
    expect(params.get('from')).toBe('2026-09-01');
    expect(params.has('to')).toBeFalse();
    expect(params.get('search')).toBe('kavya');
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe('30');
    req.flush({
      success: true,
      message: 'OK',
      data: { content: [], page: 1, size: 30, totalElements: 0, totalPages: 0, first: false, last: true },
    });
  });

  it('list() with no filters requests a clean first page', () => {
    service.list({ page: 0, size: 15 }).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/audit');
    const params = req.request.params;
    expect(params.keys().length).toBe(2);
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('15');
    req.flush({
      success: true,
      message: 'OK',
      data: { content: [], page: 0, size: 15, totalElements: 0, totalPages: 0, first: true, last: true },
    });
  });

  it('entityTypes() hits the facet endpoint', () => {
    service.entityTypes().subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/audit/entity-types');
    req.flush({ success: true, message: 'OK', data: ['EMPLOYEE', 'LEAVE_REQUEST'] });
  });

  it('exportCsv() builds the export URL from the same filters, without paging', () => {
    const url = service.exportCsv({
      action: 'LEAVE_REQUEST_APPROVED',
      entityType: undefined,
      from: '2026-09-01',
      to: undefined,
      search: 'priya',
      page: 3,
      size: 30,
    });
    expect(url).toContain('/api/v1/audit/export.csv');
    expect(url).toContain('action=LEAVE_REQUEST_APPROVED');
    expect(url).toContain('from=2026-09-01');
    expect(url).toContain('search=priya');
    expect(url).not.toContain('page=');
    expect(url).not.toContain('size=');
  });

  it('exportCsv() with no filters yields a bare export URL', () => {
    expect(service.exportCsv({})).toBe('/api/v1/audit/export.csv');
  });
});
