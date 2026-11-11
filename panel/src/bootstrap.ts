import { applyTheme } from './theme/applyTheme';
import { loadBrand, type Brand } from './theme/brand';
import { readColorPreference, resolveColorMode } from './theme/colorPreference';
import { deriveTheme, type ThemeTokens } from './theme/deriveTheme';

export async function bootstrap(root: HTMLElement, load: () => Promise<Brand> = loadBrand): Promise<{ brand: Brand; theme: ThemeTokens }> {
  const brand = await load();
  const theme = deriveTheme(brand.colors);
  applyTheme(root, theme, resolveColorMode(readColorPreference()));
  return { brand, theme };
}
