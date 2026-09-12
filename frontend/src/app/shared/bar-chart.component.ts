import { Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface Bar {
  label: string;
  value: number;
}

/**
 * Lightweight horizontal bar chart — zero chart-library dependencies.
 * Bars scale against the max value; an optional total enables percentage
 * suffixes. Accessible: each row exposes aria-label with the numbers.
 */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [CommonModule, MatTooltipModule],
  template: `
    <div class="bars" role="img" [attr.aria-label]="ariaLabel()">
      @for (bar of bars(); track bar.label) {
        <div class="bar-row">
          <span class="bar-label" [title]="bar.label">{{ bar.label }}</span>
          <div class="bar-track">
            <div
              class="bar-fill"
              [style.width.%]="pct(bar.value)"
              [matTooltip]="bar.value.toString()"
            ></div>
          </div>
          <span class="bar-value">{{ format(bar.value) }}</span>
        </div>
      } @empty {
        <div class="empty">No data yet</div>
      }
    </div>
  `,
  styles: [
    `
      .bars {
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .bar-row {
        display: grid;
        grid-template-columns: minmax(90px, 34%) 1fr 48px;
        align-items: center;
        gap: 10px;
      }
      .bar-label {
        font-size: 13px;
        color: rgba(0, 0, 0, 0.7);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .bar-track {
        height: 14px;
        background: rgba(0, 0, 0, 0.06);
        border-radius: 7px;
        overflow: hidden;
      }
      .bar-fill {
        height: 100%;
        border-radius: 7px;
        background: linear-gradient(90deg, #3f51b5, #5c6bc0);
        min-width: 2px;
        transition: width 0.4s ease;
      }
      .bar-value {
        font-size: 13px;
        font-weight: 600;
        text-align: right;
        font-variant-numeric: tabular-nums;
      }
      .empty {
        color: rgba(0, 0, 0, 0.45);
        font-size: 13px;
        padding: 8px 0;
      }
    `,
  ],
})
export class BarChartComponent {
  readonly bars = input.required<Bar[]>();
  /** Optional denominator for "x of total" formatting. */
  readonly total = input<number | null>(null);

  readonly maxValue = computed(() => Math.max(1, ...this.bars().map((b) => b.value)));

  readonly ariaLabel = computed(() =>
    this.bars()
      .map((b) => `${b.label}: ${this.format(b.value)}`)
      .join(', '),
  );

  pct(value: number): number {
    return Math.round((value / this.maxValue()) * 1000) / 10;
  }

  format(value: number): string {
    const total = this.total();
    if (total !== null && total > 0) {
      return `${value} (${Math.round((value / total) * 100)}%)`;
    }
    return String(value);
  }
}
