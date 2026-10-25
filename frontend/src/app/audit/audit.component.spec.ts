import { TestBed, flush, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuditComponent } from './audit.component';
import { AuditEntry, AuditService } from './audit.service';
import { ReportService } from '../shared/report.service';
import { ApiResponse, Page } from '../core/api.models';

const ENTRY: AuditEntry = {
  id: 12,
  actorId: 2,
  actorEmail: 'hr@hrgenius.local',
  actorName: 'Priya Sharma',
  actorRole: 'HR',
  action: 'LEAVE_REQUEST_APPROVED',
  entityType: 'LEAVE_REQUEST',
  entityId: 5,
  entityLabel: 'Anita Desai (EMP002)',
  details: 'CASUAL: 2026-09-07 → 2026-09-08',
  createdAt: '2026-09-14T10:15:30',
};

function pageOf(content: AuditEntry[], totalElements = content.length): ApiResponse<Page<AuditEntry>> {
  return {
    success: true,
    message: 'OK',
    data: {
      content,
      page: 0,
      size: 15,
      totalElements,
      totalPages: 1,
      first: true,
      last: true,
    },
  };
}

describe('AuditComponent', () => {
  let component: AuditComponent;
  let api: jasmine.SpyObj<AuditService>;
  let reports: jasmine.SpyObj<ReportService>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<AuditService>('AuditService', ['list', 'entityTypes', 'exportCsv']);
    reports = jasmine.createSpyObj<ReportService>('ReportService', ['download']);
    api.entityTypes.and.returnValue(of({ success: true, message: 'OK', data: ['LEAVE_REQUEST'] }));
    api.list.and.returnValue(of(pageOf([ENTRY])));

    await TestBed.configureTestingModule({
      imports: [AuditComponent],
      providers: [
        { provide: AuditService, useValue: api },
        { provide: ReportService, useValue: reports },
      ],
    }).compileComponents();

    // createComponent (not inject) so the component's own injector exists
    // and the field-initializer inject(AuditService) resolves.
    component = TestBed.createComponent(AuditComponent).componentInstance;
  });

  it('loads the first page and facets on init', fakeAsync(() => {
    component.ngOnInit();
    flush();
    expect(component.entries().length).toBe(1);
    expect(component.total()).toBe(1);
    expect(component.loading()).toBeFalse();
    expect(component.forbidden()).toBeFalse();
    expect(component.entityTypes()).toEqual(['LEAVE_REQUEST']);
    expect(api.list).toHaveBeenCalledWith(jasmine.objectContaining({ page: 0, size: component.PAGE_SIZE }));
  }));

  it('shows the restricted state on 403 instead of a generic error', fakeAsync(() => {
    api.list.and.returnValue(throwError(() => ({ status: 403 })));
    component.ngOnInit();
    flush();
    expect(component.forbidden()).toBeTrue();
    expect(component.loadError()).toBeNull();
  }));

  it('surfaces the server message on other errors', fakeAsync(() => {
    api.list.and.returnValue(throwError(() => ({ status: 500, error: { message: 'Nope' } })));
    component.ngOnInit();
    flush();
    expect(component.forbidden()).toBeFalse();
    expect(component.loadError()).toBe('Nope');
  }));

  it('resets to page 0 and re-queries when a filter changes', fakeAsync(() => {
    component.ngOnInit();
    flush();
    component.page.set(3);
    component.action.set('LEAVE_REQUEST_APPROVED');
    component.onFilterChange();
    flush();
    expect(component.page()).toBe(0);
    expect(api.list).toHaveBeenCalledWith(
      jasmine.objectContaining({ action: 'LEAVE_REQUEST_APPROVED', page: 0 }),
    );
  }));

  it('debounces search input before querying', fakeAsync(() => {
    component.ngOnInit();
    flush();
    api.list.calls.reset();
    component.onSearchInput('kavya');
    tick(200);
    expect(api.list).not.toHaveBeenCalled();
    tick(200);
    expect(api.list).toHaveBeenCalledWith(jasmine.objectContaining({ search: 'kavya' }));
  }));

  it('formats action codes as human titles', () => {
    expect(component.actionTitle('LEAVE_REQUEST_APPROVED')).toBe('Leave request approved');
    expect(component.actionTitle('PAYROLL_RUN_EXECUTED')).toBe('Payroll run executed');
    expect(component.actionTitle('EMPLOYEE_CREATED')).toBe('Employee created');
  });

  it('clears every filter and reloads', fakeAsync(() => {
    component.ngOnInit();
    flush();
    component.action.set('DOCUMENT_UPLOADED');
    component.search.set('pdf');
    component.clearFilters();
    flush();
    expect(component.action()).toBe('');
    expect(component.search()).toBe('');
    expect(component.hasFilters).toBeFalse();
  }));

  it('exports CSV through the shared downloader with the current filters', () => {
    api.exportCsv.and.returnValue(
      '/api/v1/audit/export.csv?action=LEAVE_REQUEST_APPROVED&from=2026-09-01&search=priya');
    component.ngOnInit();
    component.action.set('LEAVE_REQUEST_APPROVED');
    component.fromDate.set('2026-09-01');
    component.search.set('priya');

    component.exportCsv();

    // The component forwards its live filter state to the service…
    expect(api.exportCsv).toHaveBeenCalledWith(
      jasmine.objectContaining({
        action: 'LEAVE_REQUEST_APPROVED',
        from: '2026-09-01',
        search: 'priya',
      }),
    );
    // …and hands the returned URL to the shared blob downloader.
    expect(reports.download).toHaveBeenCalledWith(
      jasmine.stringContaining('/api/v1/audit/export.csv'),
      'audit-log.csv',
    );
  });
});
