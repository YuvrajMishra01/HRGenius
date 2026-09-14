import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

import { AuthService } from '../core/auth.service';
import {
  Employee,
  EmployeeStatus,
  EmploymentType,
  Option,
  PageResponse,
} from './employees.models';
import { EmployeesService, EmployeeQuery } from './employees.service';
import { EmployeeDialogComponent, EmployeeDialogData } from './employee-dialog.component';
import { ReportService } from '../shared/report.service';

/**
 * Employee directory (Phase 3): server-side search, filters, sorting and
 * pagination — the table only renders what the backend returns.
 */
@Component({
  selector: 'app-employees',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    MatDialogModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatChipsModule,
    MatTooltipModule,
  ],
  templateUrl: './employees.component.html',
  styleUrl: './employees.component.scss',
})
export class EmployeesComponent implements OnInit {
  private readonly employeesService = inject(EmployeesService);
  private readonly dialog = inject(MatDialog);
  private readonly reports = inject(ReportService);
  readonly auth = inject(AuthService);

  readonly displayedColumns = ['employeeCode', 'name', 'department', 'designation', 'joiningDate', 'employmentType', 'status', 'actions'];

  readonly rows = signal<Employee[]>([]);
  readonly total = signal(0);
  readonly pageSize = signal(10);
  readonly pageIndex = signal(0);
  readonly sortBy = signal('employeeCode');
  readonly sortDir = signal<'asc' | 'desc'>('asc');
  readonly loading = signal(false);
  readonly loadError = signal(false);
  /** 403 → the directory is role-restricted; distinct from an offline API. */
  readonly forbidden = signal(false);

  readonly search = signal('');
  readonly departmentId = signal<number | null>(null);
  readonly status = signal('');
  readonly employmentType = signal('');

  readonly departments = signal<Option[]>([]);

  readonly statusOptions: EmployeeStatus[] = ['ACTIVE', 'ON_LEAVE', 'RESIGNED', 'TERMINATED'];
  readonly typeOptions: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN'];

  readonly canEdit = computed(() => {
    const role = this.auth.user()?.role;
    return role === 'ADMIN' || role === 'HR';
  });
  readonly canDelete = computed(() => this.auth.user()?.role === 'ADMIN');

  private readonly searchSubject = new Subject<string>();

  ngOnInit(): void {
    this.searchSubject.pipe(debounceTime(350), distinctUntilChanged()).subscribe((term) => {
      this.search.set(term);
      this.pageIndex.set(0);
      this.load();
    });
    this.employeesService.departments().subscribe((res) => this.departments.set(res.data));
    this.load();
  }

  onSearchInput(term: string): void {
    this.searchSubject.next(term);
  }

  /** Exports the directory with the filters currently applied to the table. */
  export(format: 'csv' | 'pdf'): void {
    const params = new URLSearchParams();
    if (this.search()) params.set('search', this.search());
    if (this.status()) params.set('status', this.status());
    const qs = params.toString();
    this.reports.download(
      `/api/v1/reports/employees.${format}${qs ? '?' + qs : ''}`,
      `employees.${format}`,
    );
  }

  onFilterChange(): void {
    this.pageIndex.set(0);
    this.load();
  }

  onSort(sort: Sort): void {
    this.sortBy.set(sort.active);
    this.sortDir.set(sort.direction === 'desc' ? 'desc' : 'asc');
    this.load();
  }

  onPage(event: { pageIndex: number; pageSize: number }): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.forbidden.set(false);
    const query: EmployeeQuery = {
      search: this.search() || undefined,
      departmentId: this.departmentId(),
      status: this.status() || undefined,
      employmentType: this.employmentType() || undefined,
      page: this.pageIndex(),
      size: this.pageSize(),
      sortBy: this.sortBy(),
      sortDir: this.sortDir(),
    };
    this.employeesService.list(query).subscribe({
      next: (res) => {
        const page: PageResponse<Employee> = res.data;
        this.rows.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err?.status === 403) {
          this.forbidden.set(true);
        } else {
          this.loadError.set(true);
        }
      },
    });
  }

  openAdd(): void {
    this.openDialog({});
  }

  openEdit(employee: Employee): void {
    this.openDialog({ employee });
  }

  private openDialog(data: EmployeeDialogData): void {
    this.dialog
      .open(EmployeeDialogComponent, { data, width: '680px', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.load();
        }
      });
  }

  confirmDelete(employee: Employee): void {
    const ref = this.dialog.open(ConfirmDeleteDialog, {
      data: { name: `${employee.firstName} ${employee.lastName}`, code: employee.employeeCode },
      width: '420px',
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.employeesService.delete(employee.id).subscribe(() => this.load());
      }
    });
  }

  statusClass(status: EmployeeStatus): string {
    return status.toLowerCase().replace('_', '-');
  }
}

/** Small standalone confirmation dialog for the soft delete. */
@Component({
  selector: 'app-confirm-delete-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Deactivate employee</h2>
    <mat-dialog-content>
      <p class="confirm-text">
        Deactivate <strong>{{ data.name }}</strong> ({{ data.code }})?
        Their record and history are kept; the status becomes
        <strong>TERMINATED</strong>.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Deactivate</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .confirm-text {
        margin: 0;
        max-width: 340px;
      }
    `,
  ],
})
export class ConfirmDeleteDialog {
  readonly data = inject<{ name: string; code: string }>(MAT_DIALOG_DATA);
}
