import { COLOR_PREFERENCE_KEY, readColorPreference, resolveColorMode } from './colorPreference';

function mockSystemDark(matches: boolean) {
  window.matchMedia = ((query: string) => ({ matches, media: query })) as unknown as typeof window.matchMedia;
}

describe('colorPreference', () => {
  afterEach(() => localStorage.clear());

  it('readsAStoredPreference', () => {
    localStorage.setItem(COLOR_PREFERENCE_KEY, 'dark');
    expect(readColorPreference()).toBe('dark');
  });

  it('fallsBackToSystemWhenMissingOrInvalid', () => {
    expect(readColorPreference()).toBe('system');
    localStorage.setItem(COLOR_PREFERENCE_KEY, 'blue');
    expect(readColorPreference()).toBe('system');
  });

  it('fallsBackToSystemWhenStorageThrows', () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado'); });
    expect(readColorPreference()).toBe('system');
    spy.mockRestore();
  });

  it('resolvesSystemFromMatchMedia', () => {
    mockSystemDark(true);
    expect(resolveColorMode('system')).toBe('dark');
    mockSystemDark(false);
    expect(resolveColorMode('system')).toBe('light');
    expect(resolveColorMode('dark')).toBe('dark');
  });
});
