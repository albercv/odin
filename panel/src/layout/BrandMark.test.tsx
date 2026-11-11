import { fireEvent, render, screen } from '@testing-library/react';
import { BrandMark } from './BrandMark';
import { BrandProvider } from '../theme/BrandContext';
import { NEUTRAL_BRAND, type Brand } from '../theme/brand';
import { deriveTheme } from '../theme/deriveTheme';

const show = (brand: Brand, owner = false) =>
  render(<BrandProvider brand={brand} theme={deriveTheme(brand.colors)}><BrandMark showOwner={owner} /></BrandProvider>);

describe('BrandMark', () => {
  it('brandMarkShowsLogoOrInitials', () => {
    const { unmount } = show({ ...NEUTRAL_BRAND, name: 'Fincas Rivera', logoUrl: '/brand/l.png', logoAlt: 'Logo Fincas' });
    expect(screen.getByRole('img', { name: 'Logo Fincas' })).toHaveAttribute('src', '/brand/l.png');
    unmount();
    const second = show({ ...NEUTRAL_BRAND, name: 'Fincas Rivera' });
    expect(screen.getByText('FR')).toBeInTheDocument();
    second.unmount();
    show(NEUTRAL_BRAND);
    expect(screen.getByText('O')).toBeInTheDocument();
    expect(screen.getByText('Odín')).toBeInTheDocument();
    expect(screen.queryByText(/^de /)).toBeNull();
  });

  it('fallsBackToInitialsWhenTheLogoFails', () => {
    show({ ...NEUTRAL_BRAND, name: 'Fincas Rivera', logoUrl: '/brand/l.png', logoAlt: 'Logo Fincas' });
    fireEvent.error(screen.getByRole('img', { name: 'Logo Fincas' }));
    expect(screen.queryByRole('img')).toBeNull();
    expect(screen.getByText('FR')).toBeInTheDocument();
  });

  it('showsTheOwnerNameWhenAsked', () => {
    show({ ...NEUTRAL_BRAND, name: 'Fincas Rivera' }, true);
    expect(screen.getByText('de Fincas Rivera')).toBeInTheDocument();
  });
});
