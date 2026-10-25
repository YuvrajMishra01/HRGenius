import { Injectable, signal } from '@angular/core';

const THEME_KEY = 'hrgenius.theme';

export type ThemeName = 'light' | 'dark';

/**
 * Light/dark theming for the whole app. Toggling adds/removes the `dark`
 * class on <html>; every color in the design system is a CSS variable that
 * reacts to it (no page reload, all components update instantly).
 *
 * index.html applies the persisted class before Angular boots to avoid a
 * flash of the wrong theme.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<ThemeName>(this.readInitial());

  toggle(): void {
    this.set(this.theme() === 'dark' ? 'light' : 'dark');
  }

  set(theme: ThemeName): void {
    this.theme.set(theme);
    document.documentElement.classList.toggle('dark', theme === 'dark');
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch {
      // Storage unavailable (private mode etc.) — theme still applies live.
    }
  }

  /** aria-label for the toggle button. */
  label(): 'Switch to light theme' | 'Switch to dark theme' {
    return this.theme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme';
  }

  private readInitial(): ThemeName {
    try {
      return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }
}
