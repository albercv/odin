import type { BrandColors } from './brand';
import { contrastRatio, mixColors, shiftLightness } from './contrast';
import { DARK_NEUTRALS, DARK_TEXT_ON_ACCENT, LIGHT_NEUTRALS, SIDEBAR_TEXT, type Neutrals } from './neutrals';

export type ColorMode = 'light' | 'dark';
export type ModeTokens = Neutrals & {
  accent: string; accentText: string; accentTint: string;
  sidebarBackground: string; sidebarText: string; sidebarAccent: string;
};
export type ThemeTokens = {
  light: ModeTokens; dark: ModeTokens;
  brandBase: string; brandBaseDeep: string; brandAccentOnDark: string; brandAccentOnDarkText: string;
};

const MIN_CONTRAST = 4.5;
const STEP = 0.005;
const WHITE = '#ffffff';

function shiftUntil(hex: string, direction: 1 | -1, passes: (candidate: string) => boolean): string {
  for (let i = 0; i <= 1 / STEP; i++) {
    const candidate = shiftLightness(hex, direction * STEP * i);
    if (passes(candidate)) return candidate;
  }
  return shiftLightness(hex, direction);
}

function reaches(foreground: string, backgrounds: string[]): boolean {
  return backgrounds.every((bg) => contrastRatio(foreground, bg) >= MIN_CONTRAST);
}

function textOn(background: string, first: string, second: string): string {
  return contrastRatio(first, background) >= MIN_CONTRAST ? first : second;
}

function lightAccent(accent: string): string {
  const { surface, background, surfaceMuted } = LIGHT_NEUTRALS;
  return shiftUntil(accent, -1, (c) => reaches(c, [surface, background, surfaceMuted, mixColors(c, surface, 0.1)]));
}

function darkAccent(accent: string, sidebars: string[]): string {
  const { surface, background, surfaceMuted } = DARK_NEUTRALS;
  return shiftUntil(accent, 1, (c) => reaches(c, [surface, background, surfaceMuted, ...sidebars]));
}

function modeTokens(neutrals: Neutrals, accent: string, accentText: string, tint: number, sidebar: string, sidebarAccent: string): ModeTokens {
  return {
    ...neutrals, accent, accentText, accentTint: mixColors(accent, neutrals.surface, tint),
    sidebarBackground: sidebar, sidebarText: SIDEBAR_TEXT, sidebarAccent,
  };
}

export function deriveTheme(colors: BrandColors): ThemeTokens {
  const brandBase = shiftUntil(colors.base, -1, (c) => contrastRatio(SIDEBAR_TEXT, c) >= MIN_CONTRAST);
  const brandBaseDeep = mixColors('#000000', brandBase, 0.45);
  const onDark = darkAccent(colors.accent, [brandBase, brandBaseDeep]);
  const onDarkText = textOn(onDark, DARK_TEXT_ON_ACCENT, WHITE);
  const onLight = lightAccent(colors.accent);
  const light = modeTokens(LIGHT_NEUTRALS, onLight, textOn(onLight, WHITE, DARK_TEXT_ON_ACCENT), 0.1, brandBase, onDark);
  const dark = modeTokens(DARK_NEUTRALS, onDark, onDarkText, 0.14, brandBaseDeep, onDark);
  return { light, dark, brandBase, brandBaseDeep, brandAccentOnDark: onDark, brandAccentOnDarkText: onDarkText };
}
