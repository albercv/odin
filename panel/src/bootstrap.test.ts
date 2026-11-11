import { bootstrap } from './bootstrap';
import { NEUTRAL_BRAND, type Brand } from './theme/brand';

const brand: Brand = { name: 'Prueba', colors: { accent: '#05b4b9', base: '#283039' } };

describe('bootstrap', () => {
  beforeEach(() => {
    window.matchMedia = (() => ({ matches: false })) as unknown as typeof window.matchMedia;
  });

  it('appliesTheLoadedBrand', async () => {
    const root = document.createElement('div');
    const result = await bootstrap(root, async () => brand);
    expect(result.brand).toBe(brand);
    expect(root.style.getPropertyValue('--brand-base')).toBe(result.theme.brandBase);
    expect(root.dataset.theme).toBe('light');
  });

  it('appliesTheNeutralBrandWhenLoadingFails', async () => {
    const root = document.createElement('div');
    const result = await bootstrap(root, async () => NEUTRAL_BRAND);
    expect(result.brand).toBe(NEUTRAL_BRAND);
    expect(root.style.getPropertyValue('--accent')).toMatch(/^#[0-9a-f]{6}$/);
  });
});
