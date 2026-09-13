import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { forkJoin } from 'rxjs';

import { AuthService } from '../core/auth.service';
import {
  DOCUMENT_TYPES,
  DocumentService,
  DocumentType,
  EmployeeDocument,
  EmployeeOption,
} from './document.service';

const MB = 1024 * 1024;
const MAX_SIZE_MB = 5;

/** Documents page (Phase 11): per-employee file management. */
@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [
    DatePipe,
    TitleCasePipe,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss',
})
export class DocumentsComponent implements OnInit {
  private readonly api = inject(DocumentService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  readonly canWrite = computed(() => {
    const role = this.auth.user()?.role;
    return role === 'ADMIN' || role === 'HR';
  });
  readonly types = DOCUMENT_TYPES;

  readonly employees = signal<EmployeeOption[]>([]);
  readonly selectedEmployeeId = signal<number | null>(null);
  readonly documents = signal<EmployeeDocument[]>([]);
  readonly loading = signal(true);
  readonly serverError = signal<string | null>(null);

  readonly selectedEmployee = computed(() => {
    const id = this.selectedEmployeeId();
    return this.employees().find((e) => e.id === id) ?? null;
  });

  readonly typeCounts = computed(() => {
    const counts = new Map<DocumentType, number>();
    for (const doc of this.documents()) {
      counts.set(doc.documentType, (counts.get(doc.documentType) ?? 0) + 1);
    }
    return counts;
  });

  ngOnInit(): void {
    this.api.employeeOptions().subscribe({
      next: (res) => {
        this.employees.set(res.data);
        if (res.data.length > 0) {
          this.selectEmployee(res.data[0].id);
        } else {
          this.loading.set(false);
        }
      },
      error: () => {
        this.serverError.set('Could not load employees');
        this.loading.set(false);
      },
    });
  }

  selectEmployee(id: number): void {
    this.selectedEmployeeId.set(id);
    this.serverError.set(null);
    this.api.list(id).subscribe({
      next: (res) => {
        this.documents.set(res.data);
        this.loading.set(false);
      },
      error: () => {
        this.serverError.set('Could not load documents');
        this.loading.set(false);
      },
    });
  }

  openUpload(): void {
    const employeeId = this.selectedEmployeeId();
    if (employeeId == null) return;
    this.dialog
      .open(UploadDialog, {
        width: '440px',
        data: { employeeId, employees: this.employees(), currentEmployeeId: employeeId },
      })
      .afterClosed()
      .subscribe((changed) => {
        if (changed) this.selectEmployee(employeeId);
      });
  }

  download(doc: EmployeeDocument): void {
    this.api.download(doc.id).subscribe({
      next: (response) => {
        const disposition = response.headers.get('Content-Disposition') ?? '';
        const star = disposition.match(/filename\*=UTF-8''([^;]+)/);
        const plain = disposition.match(/filename="?([^";]+)"?/);
        const name = star ? decodeURIComponent(star[1]) : plain ? plain[1] : doc.fileName;
        const url = URL.createObjectURL(response.body as Blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = name;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.serverError.set('Download failed'),
    });
  }

  confirmDelete(doc: EmployeeDocument): void {
    this.dialog
      .open(DeleteDialog, { width: '400px', data: { doc } })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.api.delete(doc.id).subscribe({
          next: () => this.selectEmployee(doc.employeeId),
          error: () => this.serverError.set('Delete failed'),
        });
      });
  }

  formatSize(bytes: number | null): string {
    if (bytes == null) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < MB) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / MB).toFixed(1)} MB`;
  }

  typeIcon(type: DocumentType): string {
    return switchIcon(type);
  }

  typeLabel(type: DocumentType): string {
    return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
  }
}

function switchIcon(type: DocumentType): string {
  switch (type) {
    case 'RESUME':
      return 'description';
    case 'OFFER_LETTER':
      return 'mark_email_read';
    case 'ID_PROOF':
      return 'badge';
    case 'CERTIFICATE':
      return 'workspace_premium';
    case 'EXPERIENCE_LETTER':
      return 'history_edu';
    default:
      return 'draft';
  }
}

interface UploadData {
  employeeId: number;
  employees: EmployeeOption[];
  currentEmployeeId: number;
}

@Component({
  selector: 'app-upload-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Upload document</h2>
    <mat-dialog-content>
      @if (serverError(); as error) {
        <div class="alert"><mat-icon inline>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Employee</mat-label>
        <mat-select [(ngModel)]="employeeId" disabled>
          @for (e of data.employees; track e.id) {
            <mat-option [value]="e.id">{{ e.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Document type</mat-label>
        <mat-select [(ngModel)]="documentType">
          @for (t of types; track t) {
            <mat-option [value]="t">{{ typeLabel(t) }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <label class="file-pick" [class.has-file]="fileName()">
        <input type="file" accept=".pdf,.doc,.docx,.jpg,.jpeg,.png" (change)="onFile($event)" />
        @if (fileName(); as name) {
          <mat-icon inline>insert_drive_file</mat-icon><span>{{ name }}</span>
          <small>{{ sizeLabel() }}</small>
        } @else {
          <mat-icon inline>upload_file</mat-icon><span>Choose a file…</span>
          <small>PDF, Word or image · up to {{ maxSizeMb }} MB</small>
        }
      </label>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!file() || !documentType || busy()" (click)="save()">
        {{ busy() ? 'Uploading…' : 'Upload' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; }
    .alert {
      display: flex; gap: 8px; align-items: center;
      background: #fdecea; color: #b71c1c; border-radius: 6px;
      padding: 8px 12px; margin-bottom: 12px; font-size: 13px;
    }
    .file-pick {
      display: flex; align-items: center; gap: 10px;
      border: 2px dashed #b0bec5; border-radius: 8px;
      padding: 18px 14px; cursor: pointer; color: #546e7a;
      flex-direction: column; text-align: center;
    }
    .file-pick.has-file { border-color: #43a047; color: #2e7d32; }
    .file-pick input { display: none; }
  `,
})
export class UploadDialog {
  readonly data = inject<{ employeeId: number; employees: EmployeeOption[]; currentEmployeeId: number }>(MAT_DIALOG_DATA);
  private readonly api = inject(DocumentService);
  private readonly dialogRef = inject<MatDialogRef<UploadDialog, boolean>>(MatDialogRef);

  readonly types = DOCUMENT_TYPES;
  readonly maxSizeMb = MAX_SIZE_MB;
  readonly employeeId = this.data.currentEmployeeId;
  documentType: DocumentType | null = null;
  readonly file = signal<File | null>(null);
  readonly busy = signal(false);
  readonly serverError = signal<string | null>(null);

  readonly fileName = computed(() => this.file()?.name ?? null);

  sizeLabel(): string {
    const f = this.file();
    return f ? `${(f.size / MB).toFixed(2)} MB` : '';
  }

  typeLabel(type: DocumentType): string {
    return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const picked = input.files?.[0] ?? null;
    this.serverError.set(null);
    if (!picked) return;
    if (picked.size > MAX_SIZE_MB * MB) {
      this.serverError.set(`File exceeds the ${MAX_SIZE_MB} MB limit`);
      input.value = '';
      return;
    }
    this.file.set(picked);
  }

  save(): void {
    const picked = this.file();
    if (!picked || !this.documentType || this.busy()) return;
    this.busy.set(true);
    this.api.upload(this.data.employeeId, this.documentType, picked).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err) => {
        this.busy.set(false);
        this.serverError.set(err?.error?.message ?? 'Upload failed');
      },
    });
  }
}

@Component({
  selector: 'app-delete-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Delete document</h2>
    <mat-dialog-content>
      <p>Delete <strong>{{ data.doc.fileName }}</strong> permanently? The file is removed from storage and cannot be recovered.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" (click)="confirm()">Delete</button>
    </mat-dialog-actions>
  `,
})
export class DeleteDialog {
  readonly data = inject<{ doc: EmployeeDocument }>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<DeleteDialog, boolean>>(MatDialogRef);

  confirm(): void {
    this.dialogRef.close(true);
  }
}
