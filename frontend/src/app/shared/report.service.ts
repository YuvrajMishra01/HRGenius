import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/**
 * Report downloads (Phase 15). Fetches raw bytes as a blob and hands them
 * to the browser with a suggested file name — the reports endpoints are
 * deliberately outside the ApiResponse envelope, so there is nothing to
 * unwrap here, just save.
 */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly snack = inject(MatSnackBar);

  download(path: string, fileName: string): void {
    this.http.get(path, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () =>
        this.snack.open('Could not download the report', 'Close', { duration: 4000 }),
    });
  }
}
