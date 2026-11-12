const FORBIDDEN = ['05b4b9', '283039', 'e2d', 'evolve'];
// Sin @types/node: el especificador calculado deja el módulo como any. Vite no puede dar el texto de los *.module.css.
const fsModule: string = 'node:fs';
const { readdirSync, readFileSync } = await import(/* @vite-ignore */ fsModule);
const srcDir = 'src'; // vitest corre desde panel/

describe('noBrandInSource', () => {
  it('sourceHasNoBrandValues', () => {
    const paths: string[] = readdirSync(srcDir, { recursive: true }).filter((p: string) => /\.(ts|tsx|css)$/.test(p));
    const offenders = paths
      .filter((path) => !/\.test\.tsx?$/.test(path) && !path.endsWith('api/types.ts'))
      .filter((path) => FORBIDDEN.some((word) => String(readFileSync(`${srcDir}/${path}`)).toLowerCase().includes(word)))
      .map((path) => `src/${path}`);
    expect(paths.length).toBeGreaterThan(10);
    expect(paths.some((path) => path.endsWith('.module.css'))).toBe(true);
    expect(offenders).toEqual([]);
  });
});
export {};
