const FORBIDDEN = ['window', 'confirm'].join('.');
const sources = import.meta.glob<string>('/src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true });

test('noWindowConfirm', () => {
  const offenders = Object.entries(sources)
    .filter(([path, text]) => !path.endsWith('noWindowConfirm.test.ts') && text.includes(FORBIDDEN))
    .map(([path]) => path);
  expect(Object.keys(sources).length).toBeGreaterThan(20);
  expect(offenders).toEqual([]);
});
