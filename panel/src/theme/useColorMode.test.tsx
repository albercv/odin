import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { BrandProvider } from './BrandContext';
import { NEUTRAL_BRAND } from './brand';
import { COLOR_PREFERENCE_KEY } from './colorPreference';
import { deriveTheme } from './deriveTheme';
import { useColorMode } from './useColorMode';

const theme = deriveTheme(NEUTRAL_BRAND.colors);
const wrapper = ({ children }: { children: ReactNode }) => <BrandProvider brand={NEUTRAL_BRAND} theme={theme}>{children}</BrandProvider>;

let systemDark = false;
let listeners: (() => void)[] = [];
beforeEach(() => {
  systemDark = false; listeners = []; localStorage.clear();
  vi.stubGlobal('matchMedia', () => ({
    get matches() { return systemDark; },
    addEventListener: (_: string, l: () => void) => listeners.push(l),
    removeEventListener: (_: string, l: () => void) => { listeners = listeners.filter((x) => x !== l); },
  }));
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

it('colorModeFollowsTheSystem', () => {
  systemDark = true;
  const { result } = renderHook(() => useColorMode(), { wrapper });
  expect(result.current).toMatchObject({ mode: 'dark', preference: 'system' });
  expect(document.documentElement.dataset.theme).toBe('dark');
});

it('colorModeKeepsTheChoice', () => {
  const { result } = renderHook(() => useColorMode(), { wrapper });
  act(() => result.current.setPreference('dark'));
  expect(result.current.mode).toBe('dark');
  expect(localStorage.getItem(COLOR_PREFERENCE_KEY)).toBe('dark');
  expect(renderHook(() => useColorMode(), { wrapper }).result.current.preference).toBe('dark');
});

it('colorModeSurvivesBlockedStorage', () => {
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
  const { result } = renderHook(() => useColorMode(), { wrapper });
  act(() => result.current.setPreference('dark'));
  expect(result.current.mode).toBe('dark');
});

it('followsSystemChangesWhenPreferenceIsSystem', () => {
  const { result } = renderHook(() => useColorMode(), { wrapper });
  expect(result.current.mode).toBe('light');
  systemDark = true;
  act(() => listeners.forEach((l) => l()));
  expect(result.current.mode).toBe('dark');
  act(() => result.current.setPreference('light'));
  expect(listeners).toHaveLength(0);
});
