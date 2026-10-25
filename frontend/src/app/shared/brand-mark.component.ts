import { Component, computed, input } from '@angular/core';

/**
 * Original HRGenius brand mark: three abstract "people" dots gathered inside
 * a rounded container, with a smaller companion dot — a people/orbit motif
 * that reads at 16px and 96px alike. Pure inline SVG, theme-aware via
 * currentColor. Deliberately not derived from any other product's logo.
 */
@Component({
  selector: 'app-brand-mark',
  standalone: true,
  template: `
    <span
      class="mark"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [attr.aria-hidden]="true"
    >
      <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" focusable="false">
        <circle cx="11" cy="12" r="3.4" fill="currentColor" />
        <circle cx="21" cy="12" r="3.4" fill="currentColor" opacity="0.62" />
        <circle cx="16" cy="21.5" r="3.4" fill="currentColor" opacity="0.85" />
        <circle
          cx="16"
          cy="16"
          r="14.2"
          stroke="currentColor"
          stroke-width="1.6"
          opacity="0.35"
          stroke-dasharray="66 24"
          stroke-linecap="round"
        />
      </svg>
    </span>
  `,
  styles: [
    `
      .mark {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        color: inherit;
      }
      svg {
        width: 100%;
        height: 100%;
        display: block;
      }
    `,
  ],
})
export class BrandMarkComponent {
  readonly size = input(30);
  readonly tone = input<'on-primary' | 'auto'>('auto');

  readonly fontSize = computed(() => Math.round(this.size() * 0.5));
}
