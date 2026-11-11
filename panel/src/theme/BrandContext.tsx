import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { NEUTRAL_BRAND, type Brand } from './brand';
import { deriveTheme, type ThemeTokens } from './deriveTheme';

type BrandValue = { brand: Brand; theme: ThemeTokens };

const BrandContext = createContext<BrandValue>({ brand: NEUTRAL_BRAND, theme: deriveTheme(NEUTRAL_BRAND.colors) });

export function BrandProvider({ brand, theme, children }: BrandValue & { children: ReactNode }) {
  const value = useMemo(() => ({ brand, theme }), [brand, theme]);
  return <BrandContext.Provider value={value}>{children}</BrandContext.Provider>;
}

export function useBrand(): Brand {
  return useContext(BrandContext).brand;
}

export function useThemeTokens(): ThemeTokens {
  return useContext(BrandContext).theme;
}
