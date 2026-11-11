const fsModule: string = 'node:fs';
const { readFileSync } = await import(/* @vite-ignore */ fsModule);
const css = (path: string): string => String(readFileSync(`src/${path}`));

describe('brandSurfaces', () => {
  it('shadeAndBrainStayOutOfTheFlow', () => {
    expect(css('brain/BrainCanvas.module.css')).toMatch(/\.brain \{[^}]*position: absolute; inset: 0/);
    for (const file of ['pages/Login.module.css', 'pages/Cover.module.css']) {
      expect(css(file)).toContain(':not(:first-child):not(.shade)');
      expect(css(file)).toMatch(/\.shade \{[^}]*position: absolute; inset: 0/);
    }
  });

  it('usesNoLiteralColoursBeyondWhiteOnBrandSurfaces', () => {
    for (const file of ['layout/Sidebar.module.css', 'pages/Login.module.css', 'pages/Cover.module.css']) {
      const literals = css(file).match(/#[0-9a-fA-F]{3,6}\b/g) ?? [];
      expect(literals.filter((c) => c.toLowerCase() !== '#fff')).toEqual([]);
    }
  });
});
export {};
