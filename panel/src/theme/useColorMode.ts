import { useCallback, useEffect, useState } from 'react';
import { applyTheme } from './applyTheme';
import { COLOR_PREFERENCE_KEY, readColorPreference, resolveColorMode, type ColorPreference } from './colorPreference';
import { useThemeTokens } from './BrandContext';
import type { ColorMode } from './deriveTheme';

const DARK_QUERY = '(prefers-color-scheme: dark)';

export function useColorMode(): { mode: ColorMode; preference: ColorPreference; setPreference(p: ColorPreference): void } {
  const theme = useThemeTokens();
  const [preference, setStored] = useState<ColorPreference>(readColorPreference);
  const [mode, setMode] = useState<ColorMode>(() => resolveColorMode(preference));

  useEffect(() => {
    const sync = () => setMode(resolveColorMode(preference));
    sync();
    if (preference !== 'system') return undefined;
    const query = window.matchMedia(DARK_QUERY);
    query.addEventListener('change', sync);
    return () => query.removeEventListener('change', sync);
  }, [preference]);

  useEffect(() => applyTheme(document.documentElement, theme, mode), [theme, mode]);

  const setPreference = useCallback((next: ColorPreference) => {
    setStored(next);
    try { localStorage.setItem(COLOR_PREFERENCE_KEY, next); } catch { /* storage blocked: keep in memory */ }
  }, []);
  return { mode, preference, setPreference };
}
