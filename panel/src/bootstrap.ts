import { applyTheme } from './theme/applyTheme';
import { loadBrand, type Brand } from './theme/brand';
import { readColorPreference, resolveColorMode } from './theme/colorPreference';
import { deriveTheme, type ThemeTokens } from './theme/deriveTheme';

function setFavicon(logoUrl?: string) {
  if (!logoUrl) return;
  let link = document.head.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (!link) {
    link = document.createElement('link');
    link.rel = 'icon';
    document.head.append(link);
  }
  link.href = logoUrl;
}

export async function bootstrap(root: HTMLElement, load: () => Promise<Brand> = loadBrand): Promise<{ brand: Brand; theme: ThemeTokens }> {
  const brand = await load();
  const theme = deriveTheme(brand.colors);
  applyTheme(root, theme, resolveColorMode(readColorPreference()));
  setFavicon(brand.logoUrl);
  return { brand, theme };
}
