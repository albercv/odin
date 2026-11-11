import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { Cover } from './Cover';
import { BrandProvider } from '../theme/BrandContext';
import { NEUTRAL_BRAND } from '../theme/brand';
import { deriveTheme } from '../theme/deriveTheme';

vi.mock('../brain/BrainCanvas', () => ({ BrainCanvas: () => <div data-testid="brain" /> }));

const brand = { ...NEUTRAL_BRAND, cover: { title: 'Titular', subtitle: 'Subtítulo', note: 'Nota' } };
const show = (onStart = () => {}) =>
  render(<BrandProvider brand={brand} theme={deriveTheme(brand.colors)}><Cover onStart={onStart} /></BrandProvider>);

describe('Cover', () => {
  it('coverShowsTheBrandTexts', async () => {
    const onStart = vi.fn();
    show(onStart);
    expect(screen.getByRole('heading', { name: 'Titular' })).toBeInTheDocument();
    expect(screen.getByText('Subtítulo')).toBeInTheDocument();
    expect(screen.getByText('Nota')).toBeInTheDocument();
    expect(screen.getByTestId('brain')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Empezar la demostración' }));
    expect(onStart).toHaveBeenCalled();
  });

  it('coverMakesNoApiCalls', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    show();
    expect(fetchMock).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
