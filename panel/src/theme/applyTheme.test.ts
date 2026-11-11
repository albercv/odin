import { applyTheme } from './applyTheme';
import { deriveTheme } from './deriveTheme';

const VARIABLES = ['--bg', '--surface', '--surface-muted', '--line', '--line-strong', '--text', '--text-muted', '--accent', '--accent-text',
  '--accent-tint', '--pending-text', '--pending-bg', '--danger', '--danger-bg', '--side-bg', '--side-text', '--side-accent',
  '--brand-base', '--brand-base-deep', '--brand-accent-on-dark', '--brand-accent-on-dark-text'];
const theme = deriveTheme({ accent: '#05b4b9', base: '#283039' });

describe('applyTheme', () => {
  it('writesEveryVariableForTheMode', () => {
    for (const mode of ['light', 'dark'] as const) {
      const root = document.createElement('div');
      applyTheme(root, theme, mode);
      for (const name of VARIABLES) expect(root.style.getPropertyValue(name), `${mode} ${name}`).toMatch(/^#[0-9a-f]{6}$/);
      expect(root.style.getPropertyValue('--surface')).toBe(theme[mode].surface);
      expect(root.style.getPropertyValue('--side-bg')).toBe(mode === 'light' ? theme.brandBase : theme.brandBaseDeep);
    }
  });

  it('setsTheThemeDataAttribute', () => {
    const root = document.createElement('div');
    applyTheme(root, theme, 'dark');
    expect(root.dataset.theme).toBe('dark');
    applyTheme(root, theme, 'light');
    expect(root.dataset.theme).toBe('light');
  });
});
