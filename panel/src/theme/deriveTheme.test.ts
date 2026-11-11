import { contrastRatio } from './contrast';
import { deriveTheme, type ModeTokens } from './deriveTheme';

const E2D = { accent: '#05b4b9', base: '#283039' };
const ACCENTS = ['#05b4b9', '#ffd400', '#c6ff00', '#f8c8dc', '#b3e5fc', '#111111', '#808080', '#ff0000', '#00ff00', '#0000ff', '#ffffff', '#7a5c00', '#4f6d8f'];
const BASES = ['#283039', '#2e2427', '#9aa5b1'];
const MIN = 4.5;

function hueAndSaturated(hex: string): { h: number; s: number } {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255) as [number, number, number];
  const max = Math.max(r, g, b);
  const d = max - Math.min(r, g, b);
  const h = d === 0 ? 0 : max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return { h: (h * 60 + 360) % 360, s: d };
}

function textPairs(t: ModeTokens, sidebarBackground: string): [string, string, string][] {
  return [
    ['text', t.text, t.background], ['text', t.text, t.surface], ['text', t.text, t.surfaceMuted],
    ['textMuted', t.textMuted, t.surface], ['textMuted', t.textMuted, t.surfaceMuted],
    ['pending', t.pendingText, t.pendingBackground], ['danger', t.danger, t.surface],
    ['accentText', t.accentText, t.accent], ['sidebarText', t.sidebarText, sidebarBackground],
    ['sidebarAccent', t.sidebarAccent, sidebarBackground],
    ['accent', t.accent, t.surface], ['accent', t.accent, t.background], ['accent', t.accent, t.surfaceMuted],
  ];
}

describe('deriveTheme', () => {
  it('e2dLightAccentPassesOnEveryLightBackground', () => {
    const { light } = deriveTheme(E2D);
    for (const bg of [light.surface, light.background, light.surfaceMuted, light.accentTint]) {
      expect(contrastRatio(light.accent, bg)).toBeGreaterThanOrEqual(MIN);
    }
    expect(contrastRatio(light.accentText, light.accent)).toBeGreaterThanOrEqual(MIN);
  });

  it('e2dDarkAccentIsTheLogoTeal', () => {
    const theme = deriveTheme(E2D);
    expect(theme.brandAccentOnDark).toBe('#05b4b9');
    expect(theme.dark.accent).toBe('#05b4b9');
  });

});

describe('deriveTheme contrast', () => {
  it('everyTextPairReachesFourAndAHalf', () => {
    for (const accent of ACCENTS) {
      for (const base of BASES) {
        const theme = deriveTheme({ accent, base });
        const sets: [string, ModeTokens, string][] = [['light', theme.light, theme.brandBase], ['dark', theme.dark, theme.brandBaseDeep]];
        for (const [mode, tokens, side] of sets) {
          for (const [name, fg, bg] of textPairs(tokens, side)) {
            expect(contrastRatio(fg, bg), `${accent}/${base} ${mode} ${name}`).toBeGreaterThanOrEqual(MIN);
          }
        }
        expect(contrastRatio(theme.dark.accent, theme.brandBase)).toBeGreaterThanOrEqual(MIN);
        expect(contrastRatio(theme.brandAccentOnDarkText, theme.brandAccentOnDark)).toBeGreaterThanOrEqual(MIN);
      }
    }
  });

});

describe('deriveTheme hue and base', () => {
  it('keepsTheHueOfTheAccent', () => {
    for (const accent of ACCENTS) {
      const original = hueAndSaturated(accent);
      if (original.s < 0.1) continue;
      const theme = deriveTheme({ accent, base: '#283039' });
      for (const derived of [theme.light.accent, theme.dark.accent]) {
        const diff = Math.abs(hueAndSaturated(derived).h - original.h);
        expect(Math.min(diff, 360 - diff), `${accent} -> ${derived}`).toBeLessThanOrEqual(2);
      }
    }
  });

  it('darkensABaseThatIsTooLight', () => {
    const theme = deriveTheme({ accent: '#05b4b9', base: '#9aa5b1' });
    expect(theme.brandBase).not.toBe('#9aa5b1');
    expect(contrastRatio('#c5ced4', theme.brandBase)).toBeGreaterThanOrEqual(MIN);
    expect(deriveTheme(E2D).brandBase).toBe('#283039');
  });
});
