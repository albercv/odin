import type { ColorMode } from './deriveTheme';

export type ColorPreference = 'light' | 'dark' | 'system';
export const COLOR_PREFERENCE_KEY = 'odin.colorPreference';

export function readColorPreference(): ColorPreference {
  try {
    const stored = localStorage.getItem(COLOR_PREFERENCE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    return 'system';
  }
}

export function resolveColorMode(preference: ColorPreference): ColorMode {
  if (preference !== 'system') return preference;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}
