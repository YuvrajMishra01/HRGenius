import { TestBed } from '@angular/core/testing';

import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    service = TestBed.inject(ThemeService);
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  it('defaults to light when nothing is persisted', () => {
    expect(service.theme()).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBeFalse();
  });

  it('toggles to dark, updates the html class and persists the choice', () => {
    service.toggle();

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBeTrue();
    expect(localStorage.getItem('hrgenius.theme')).toBe('dark');
  });

  it('toggling twice returns to light', () => {
    service.toggle();
    service.toggle();

    expect(service.theme()).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBeFalse();
    expect(localStorage.getItem('hrgenius.theme')).toBe('light');
  });

  it('restores a persisted dark theme on startup', () => {
    localStorage.setItem('hrgenius.theme', 'dark');
    // Reset so a fresh instance re-reads localStorage at construction.
    TestBed.resetTestingModule();
    const restored = TestBed.inject(ThemeService);

    expect(restored.theme()).toBe('dark');
  });
});
