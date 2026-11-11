const FORBIDDEN = ['05b4b9', '283039', 'e2d', 'evolve'];
// import.meta.glob evita depender de @types/node para leer el código fuente
const files = import.meta.glob('../**/*.{ts,tsx,css}', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;

describe('noBrandInSource', () => {
  it('sourceHasNoBrandValues', () => {
    const offenders = Object.entries(files)
      .filter(([path]) => !/\.test\.tsx?$/.test(path) && !path.endsWith('/api/types.ts'))
      .filter(([, content]) => FORBIDDEN.some((word) => content.toLowerCase().includes(word)))
      .map(([path]) => path);
    expect(Object.keys(files).length).toBeGreaterThan(10);
    expect(offenders).toEqual([]);
  });
});
