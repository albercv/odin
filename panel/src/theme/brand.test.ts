import { NEUTRAL_BRAND, loadBrand, parseBrand } from './brand';

const E2D = {
  name: 'E2D', logo: 'logo.webp', logoAlt: 'E2D', colors: { accent: '#05b4b9', base: '#283039' },
  cover: { title: 'Odín, el cerebro digital del despacho', subtitle: 'Lo que tu despacho ya resolvió.', note: 'Demostración con casos ficticios' },
};
const MINIMAL = { colors: { accent: '#05b4b9', base: '#283039' } };

describe('parseBrand', () => {
  it('acceptsTheE2dBrand', () => {
    expect(parseBrand(E2D)).toMatchObject({ name: 'E2D', logoAlt: 'E2D', colors: E2D.colors, cover: E2D.cover });
  });

  it('logoIsOptional', () => {
    expect(parseBrand(MINIMAL)).toEqual({ colors: MINIMAL.colors });
  });

  it('resolvesTheLogoInsideTheBrandFolder', () => {
    expect(parseBrand(E2D)?.logoUrl).toBe('/brand/logo.webp');
  });

  it('rejectsLogoPathsOutsideTheFolder', () => {
    for (const logo of ['../x.png', '/etc/x.png', 'https://a/x.png']) {
      expect(parseBrand({ ...E2D, logo })).toBeUndefined();
    }
  });

});

describe('parseBrand validation', () => {
  it('rejectsInvalidColors', () => {
    for (const accent of ['#fff', 'teal', '#05b4bg', '05b4b9', '#05b4b9ff']) {
      expect(parseBrand({ ...E2D, colors: { accent, base: '#283039' } })).toBeUndefined();
    }
    expect(parseBrand({ ...E2D, colors: { accent: '#05b4b9' } })).toBeUndefined();
  });

  it('rejectsUnknownLogoExtensions', () => {
    expect(parseBrand({ ...E2D, logo: 'logo.gif' })).toBeUndefined();
    expect(parseBrand({ ...E2D, logo: 'logo.html' })).toBeUndefined();
  });

  it('rejectsOverlongTexts', () => {
    expect(parseBrand({ ...E2D, name: 'x'.repeat(61) })).toBeUndefined();
    expect(parseBrand({ ...E2D, logoAlt: 'x'.repeat(81) })).toBeUndefined();
    expect(parseBrand({ ...E2D, cover: { ...E2D.cover, title: 'x'.repeat(81) } })).toBeUndefined();
    expect(parseBrand({ ...E2D, cover: { ...E2D.cover, subtitle: 'x'.repeat(161) } })).toBeUndefined();
    expect(parseBrand({ ...E2D, cover: { ...E2D.cover, note: 'x'.repeat(101) } })).toBeUndefined();
    expect(parseBrand({ ...E2D, name: 'x'.repeat(60) })).toBeDefined();
  });

  it('rejectsAnIncompleteCover', () => {
    expect(parseBrand({ ...E2D, cover: { title: 'a', subtitle: 'b' } })).toBeUndefined();
  });
});

describe('loadBrand', () => {
  beforeEach(() => { vi.spyOn(console, 'error').mockImplementation(() => undefined); });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  it('loadsAValidBrand', async () => {
    expect((await loadBrand(async () => E2D)).name).toBe('E2D');
  });

  it('fallsBackToNeutralWhenMissing', async () => {
    const brand = await loadBrand(async () => { throw new Error('404'); });
    expect(brand).toBe(NEUTRAL_BRAND);
    expect(console.error).toHaveBeenCalledWith(expect.stringContaining('404'));
  });

  it('fallsBackToNeutralWhenInvalid', async () => {
    expect(await loadBrand(async () => ({ colors: { accent: 'red' } }))).toBe(NEUTRAL_BRAND);
    expect(console.error).toHaveBeenCalledWith(expect.stringContaining('no es válida'));
  });

  it('fallsBackToNeutralAfterTwoSeconds', async () => {
    vi.useFakeTimers();
    const pending = loadBrand(() => new Promise(() => undefined));
    await vi.advanceTimersByTimeAsync(1999);
    expect(console.error).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(await pending).toBe(NEUTRAL_BRAND);
    expect(console.error).toHaveBeenCalledWith(expect.stringContaining('2000'));
  });

  it('hasTheNeutralColors', () => {
    expect(NEUTRAL_BRAND).toEqual({ colors: { accent: '#4f6d8f', base: '#2b3036' } });
  });
});
