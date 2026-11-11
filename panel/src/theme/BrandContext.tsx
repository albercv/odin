import { createContext, useContext, type ReactNode } from 'react';
import { NEUTRAL_BRAND, type Brand } from './brand';
import type { ThemeTokens } from './deriveTheme';

const BrandContext = createContext<Brand>(NEUTRAL_BRAND);

export function BrandProvider({ brand, children }: { brand: Brand; theme: ThemeTokens; children: ReactNode }) {
  return <BrandContext.Provider value={brand}>{children}</BrandContext.Provider>;
}

export function useBrand(): Brand {
  return useContext(BrandContext);
}
