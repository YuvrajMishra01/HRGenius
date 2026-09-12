import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { EmployeesService } from './employees.service';

describe('EmployeesService', () => {
  let service: EmployeesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EmployeesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() sends query params and only non-empty filters', () => {
    service
      .list({
        search: 'rohan',
        departmentId: 1,
        status: 'ACTIVE',
        employmentType: undefined,
        page: 2,
        size: 25,
        sortBy: 'firstName',
        sortDir: 'desc',
      })
      .subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/employees',
    );
    const params = req.request.params;
    expect(params.get('search')).toBe('rohan');
    expect(params.get('departmentId')).toBe('1');
    expect(params.get('status')).toBe('ACTIVE');
    expect(params.has('employmentType')).toBeFalse();
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('25');
    expect(params.get('sortBy')).toBe('firstName');
    expect(params.get('sortDir')).toBe('desc');
    req.flush({ success: true, message: 'OK', data: { content: [], page: 2, size: 25, totalElements: 0, totalPages: 0, first: false, last: true } });
  });

  it('create() posts the request body', () => {
    service
      .create({
        employeeCode: 'EMP950',
        firstName: 'A',
        lastName: 'B',
        email: 'a@b.c',
        phone: null,
        dateOfBirth: null,
        gender: null,
        address: null,
        joiningDate: '2026-01-01',
        employmentType: 'FULL_TIME',
        status: 'ACTIVE',
        departmentId: 1,
        designationId: null,
        managerId: null,
      })
      .subscribe();

    const req = httpMock.expectOne('/api/v1/employees');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.employeeCode).toBe('EMP950');
    req.flush({ success: true, message: 'Created', data: { id: 99 } });
  });

  it('designations() passes departmentId when given', () => {
    service.designations(2).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/designations');
    expect(req.request.params.get('departmentId')).toBe('2');
    req.flush({ success: true, message: 'OK', data: [] });
  });
});
