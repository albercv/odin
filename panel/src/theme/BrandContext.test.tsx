import { render, screen } from '@testing-library/react';
import { BrandProvider, useBrand, useThemeTokens } from './BrandContext';
import { deriveTheme } from './deriveTheme';

const brand = { name: 'Prueba', colors: { accent: '#05b4b9', base: '#283039' } };

function Probe() {
  return <p>{useBrand().name}</p>;
}

function ThemeProbe() {
  return <p>{useThemeTokens().brandBase}</p>;
}

describe('BrandContext', () => {
  it('providesTheThemeTokensToChildren', () => {
    const theme = deriveTheme(brand.colors);
    render(<BrandProvider brand={brand} theme={theme}><ThemeProbe /></BrandProvider>);
    expect(screen.getByText(theme.brandBase)).toBeInTheDocument();
  });

  it('providesTheBrandToChildren', () => {
    render(<BrandProvider brand={brand} theme={deriveTheme(brand.colors)}><Probe /></BrandProvider>);
    expect(screen.getByText('Prueba')).toBeInTheDocument();
  });
});
