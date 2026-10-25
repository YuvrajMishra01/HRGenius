import { Component, computed, input } from '@angular/core';

/** Initials avatar with a deterministic hue derived from the name. */
@Component({
  selector: 'app-avatar',
  standalone: true,
  template: `
    <span
      class="avatar"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [style.font-size.px]="fontSize()"
      [style.background]="background()"
      aria-hidden="true"
    >{{ initials() }}</span>
  `,
  styles: [
    `
      .avatar {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 50%;
        color: #fff;
        font-weight: 700;
        letter-spacing: 0.02em;
        flex-shrink: 0;
        user-select: none;
      }
    `,
  ],
})
export class AvatarComponent {
  readonly name = input.required<string>();
  readonly size = input(32);

  readonly initials = computed(() => {
    const parts = this.name()
      .trim()
      .split(/\s+/)
      .filter(Boolean);
    if (parts.length === 0) {
      return '?';
    }
    const first = parts[0].charAt(0);
    const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
    return (first + last).toUpperCase();
  });

  readonly fontSize = computed(() => Math.max(9, Math.round(this.size() * 0.38)));

  /** Deterministic pleasant hue from the name so colors are stable per person. */
  readonly background = computed(() => {
    const name = this.name();
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
    }
    const hue = hash % 360;
    return `hsl(${hue} 58% 46%)`;
  });
}
