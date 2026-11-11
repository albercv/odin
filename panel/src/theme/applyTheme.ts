import type { ColorMode, ModeTokens, ThemeTokens } from './deriveTheme';

function modeVariables(t: ModeTokens): Record<string, string> {
  return {
    '--bg': t.background, '--surface': t.surface, '--surface-muted': t.surfaceMuted, '--line': t.line,
    '--line-strong': t.lineStrong, '--text': t.text, '--text-muted': t.textMuted, '--accent': t.accent,
    '--accent-text': t.accentText, '--accent-tint': t.accentTint, '--pending-text': t.pendingText,
    '--pending-bg': t.pendingBackground, '--danger': t.danger, '--danger-bg': t.dangerBackground,
    '--side-bg': t.sidebarBackground, '--side-text': t.sidebarText, '--side-accent': t.sidebarAccent,
  };
}

export function applyTheme(root: HTMLElement, theme: ThemeTokens, mode: ColorMode): void {
  const variables = {
    ...modeVariables(theme[mode]),
    '--brand-base': theme.brandBase, '--brand-base-deep': theme.brandBaseDeep,
    '--brand-accent-on-dark': theme.brandAccentOnDark, '--brand-accent-on-dark-text': theme.brandAccentOnDarkText,
  };
  for (const [name, value] of Object.entries(variables)) root.style.setProperty(name, value);
  root.dataset.theme = mode;
}
