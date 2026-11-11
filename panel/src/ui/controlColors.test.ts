// Sin @types/node: el especificador calculado deja el módulo como any. Los *.module.css llegan vacíos a Vitest.
const fsModule: string = 'node:fs';
const { readFileSync } = await import(/* @vite-ignore */ fsModule);
const rule = (file: string, selector: string): string =>
  String(readFileSync(`src/ui/${file}`)).match(new RegExp(`${selector.replace('.', '\\.')}[^{]*\\{([^}]*)\\}`))?.[1] ?? '';

it('okPillDoesNotUseTheAccentAsTextColour', () => {
  const ok = rule('StatusPill.module.css', '.ok');
  expect(ok).toMatch(/(^|[\s;])color:\s*var\(--text\)/);
  expect(ok).toContain('background: var(--accent-tint)');
  expect(rule('StatusPill.module.css', '.ok::before')).toContain('background: var(--accent)');
});

it('switchTrackIsVisibleAgainstTheSurface', () => {
  expect(rule('Switch.module.css', '.switch input')).toContain('background: var(--text-muted)');
});
export {};
