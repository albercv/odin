import { render, screen } from '@testing-library/react';
import { BrandProvider, useBrand } from './BrandContext';
import { deriveTheme } from './deriveTheme';

const brand = { name: 'Prueba', colors: { accent: '#05b4b9', base: '#283039' } };

function Probe() {
  return <p>{useBrand().name}</p>;
}

describe('BrandContext', () => {
  it('providesTheBrandToChildren', () => {
    render(<BrandProvider brand={brand} theme={deriveTheme(brand.colors)}><Probe /></BrandProvider>);
    expect(screen.getByText('Prueba')).toBeInTheDocument();
  });
});
