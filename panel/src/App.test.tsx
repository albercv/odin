import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { App } from './App';
import { Documents } from './pages/Documents';
import { Keys } from './components/Keys';
import { mockFetch, type Routes } from './mockApi';
import { mockDialog } from './ui/dialogMock';
import { BrandProvider } from './theme/BrandContext';
import { NEUTRAL_BRAND } from './theme/brand';
import { deriveTheme } from './theme/deriveTheme';

vi.mock('./brain/BrainCanvas', () => ({ BrainCanvas: () => <div data-testid="brain" /> }));

const unauthorized = { 'GET /v1/admin/me': () => ({ status: 401, body: { detail: 'Sesión caducada' } }) };
const renderWith = (cover?: { title: string; subtitle: string; note: string }) => {
  const brand = { ...NEUTRAL_BRAND, cover };
  return render(<BrandProvider brand={brand} theme={deriveTheme(brand.colors)}><App /></BrandProvider>);
};

const company = { id: 'c1', name: 'Acme', capabilities: ['SEARCH_KNOWLEDGE'] };

test('loginShowsErrorOnBadCredentials', async () => {
  mockFetch({
    'GET /v1/admin/me': () => ({ status: 401, body: { detail: 'Sesión caducada' } }),
    'POST /v1/admin/session': () => ({ status: 401, body: { detail: 'Correo o contraseña incorrectos' } }),
  });
  render(<App />);
  await userEvent.type(await screen.findByLabelText('Correo'), 'a@b.c');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'mala');
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos');
});

test('loginStillLogsIn', async () => {
  const calls = mockFetch({
    ...unauthorized,
    'POST /v1/admin/session': () => ({ status: 204 }),
  });
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Entra en el panel' })).toBeInTheDocument();
  await userEvent.type(screen.getByLabelText('Correo'), 'a@b.c');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'buena');
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'POST /v1/admin/session')).toBe(true));
  expect(calls.find((c) => c.key === 'POST /v1/admin/session')?.body).toEqual({ email: 'a@b.c', password: 'buena' });
});

test('coverHashShowsCoverThenLogin', async () => {
  location.hash = '#cover';
  mockFetch(unauthorized);
  renderWith({ title: 'Titular', subtitle: 'Sub', note: 'Nota' });
  await userEvent.click(await screen.findByRole('button', { name: 'Empezar la demostración' }));
  expect(await screen.findByRole('heading', { name: 'Entra en el panel' })).toBeInTheDocument();
  expect(location.hash).toBe('');
});

test('coverHashWithoutCoverShowsLogin', async () => {
  location.hash = '#cover';
  mockFetch(unauthorized);
  renderWith();
  expect(await screen.findByRole('heading', { name: 'Entra en el panel' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Empezar la demostración' })).toBeNull();
  location.hash = '';
});

test('shellShowsPendingCountAndCompanyName', async () => {
  mockFetch({
    'GET /v1/admin/me': () => ({ userId: 'u', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1', personCode: 'BEA1', companies: [{ id: 'c1', name: 'Acme', code: 'ACM' }] }),
    'GET /v1/admin/companies/c1/cards?status=PENDING': () => [{ id: 'k1', title: 'F', body: 'B', status: 'PENDING' }],
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c1/members': () => [],
  });
  render(<App />);
  expect(await screen.findByRole('link', { name: /Revisión/ })).toHaveTextContent('1');
});

test('groupAdminSeesCompaniesAndCapabilities', async () => {
  mockFetch({
    'GET /v1/admin/me': () => ({ userId: 'u', displayName: 'Ada', role: 'GROUP_ADMIN' }),
    'GET /v1/admin/companies': () => [company],
  });
  render(<App />);
  expect(await screen.findByText('Acme')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Nueva empresa' })).toBeInTheDocument();
  expect(screen.getByRole('switch', { name: 'Buscar conocimiento' })).toBeChecked();
  expect(screen.getByRole('switch', { name: 'Guardar fichas' })).not.toBeChecked();
});

test('companyAdminGoesToOwnCompanyWithoutCapabilityControls', async () => {
  mockFetch({
    'GET /v1/admin/me': () => ({ userId: 'u', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1', personCode: 'BEA1', companies: [{ id: 'c1', name: 'Acme', code: 'ACM' }] }),
    'GET /v1/admin/companies': () => [company],
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c1/members': () => [],
    'GET /v1/admin/companies/c1/cards?status=PENDING': () => [],
  });
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Acme' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Nueva empresa' })).toBeNull();
  expect(screen.queryByLabelText('Buscar conocimiento')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Empresas' })).toBeNull();
});

test('failedIssueKeepsTheLabel', async () => {
  mockFetch({
    'GET /v1/admin/companies/c1/members/u1/keys': () => [],
    'POST /v1/admin/companies/c1/members/u1/keys': () => ({ status: 400, body: { detail: 'Etiqueta no válida' } }),
  });
  render(<Keys companyId="c1" userId="u1" />);
  await userEvent.type(await screen.findByLabelText('Etiqueta de la clave'), 'portátil');
  await userEvent.click(screen.getByRole('button', { name: 'Dar clave' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Etiqueta no válida');
  expect(screen.getByLabelText('Etiqueta de la clave')).toHaveValue('portátil');
});

test('revokeButtonCarriesTheKeyLabel', async () => {
  mockFetch({ 'GET /v1/admin/companies/c1/members/u1/keys': () => [{ id: 'k1', label: 'portátil', prefix: 'odin_ab' }] });
  render(<Keys companyId="c1" userId="u1" />);
  expect(await screen.findByRole('button', { name: 'Revocar portátil' })).toBeInTheDocument();
});

test('issuedKeyShownOnceWithCopy', async () => {
  mockDialog();
  const writeText = vi.fn(async () => {});
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  mockFetch({
    'GET /v1/admin/companies/c1/members/u1/keys': () => [],
    'POST /v1/admin/companies/c1/members/u1/keys': () => ({ status: 201, body: { id: 'k1', prefix: 'odin_ab', key: 'odin_ab.SECRETO' } }),
  });
  render(<Keys companyId="c1" userId="u1" />);
  await userEvent.type(await screen.findByLabelText('Etiqueta de la clave'), 'portátil');
  await userEvent.click(screen.getByRole('button', { name: 'Dar clave' }));
  expect(await screen.findByRole('dialog', { name: 'Clave nueva' })).toHaveTextContent('odin_ab.SECRETO');
  expect(screen.getByText(/No se volverá a mostrar/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Copiar' }));
  expect(writeText).toHaveBeenCalledWith('odin_ab.SECRETO');
  await userEvent.click(screen.getByRole('button', { name: 'Entendido' }));
  expect(screen.queryByText('odin_ab.SECRETO')).toBeNull();
});

test('documentsListShowsStatusAndReason', async () => {
  const calls = mockFetch({
    'GET /v1/admin/companies/c1/documents?offset=0&limit=500': () => [
      { id: 'd1', title: 'Contrato', filename: 'c.pdf', status: 'FAILED', failureReason: 'Fichero ilegible' },
      { id: 'd2', title: 'Manual', filename: 'm.txt', status: 'READY' },
    ],
    'GET /v1/admin/companies/c1/projects': () => [],
    'POST /v1/admin/documents/d1/retry': () => ({ id: 'd1', status: 'RECEIVED' }),
  });
  render(<Documents companyId="c1" />);
  const row = (await screen.findByText('Contrato')).closest('tr')!;
  expect(within(row).getByText('Fallido')).toBeInTheDocument();
  expect(within(row).getByText('Fichero ilegible')).toBeInTheDocument();
  expect(screen.getByText('Listo')).toBeInTheDocument();
  await userEvent.click(within(row).getByRole('button', { name: 'Reintentar Contrato' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'POST /v1/admin/documents/d1/retry')).toBe(true));
});

const docs = (from: number, n: number) =>
  Array.from({ length: n }, (_, i) => ({ id: `d${from + i}`, title: `Doc ${from + i}`, filename: 'f.pdf', status: 'READY' }));

test('documentsListWalksAllPagesAndRefreshes', async () => {
  const calls = mockFetch({
    'GET /v1/admin/companies/c1/documents?offset=0&limit=500': () => docs(0, 500),
    'GET /v1/admin/companies/c1/documents?offset=500&limit=500': () => docs(500, 20),
    'GET /v1/admin/companies/c1/projects': () => [],
  });
  render(<Documents companyId="c1" />);
  expect(await screen.findByText('Doc 519')).toBeInTheDocument();
  expect(screen.queryByText(/lista está cortada/)).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Actualizar' }));
  await waitFor(() => expect(calls.filter((c) => c.key.endsWith('offset=500&limit=500'))).toHaveLength(2));
});

test('documentsListWarnsWhenTruncated', async () => {
  const routes: Routes = { 'GET /v1/admin/companies/c1/projects': () => [] };
  for (let offset = 0; offset <= 2000; offset += 500) {
    routes[`GET /v1/admin/companies/c1/documents?offset=${offset}&limit=500`] = () => docs(offset, 500);
  }
  mockFetch(routes);
  render(<Documents companyId="c1" />);
  expect(await screen.findByText(/La lista está cortada: se muestran los primeros 2000 documentos/)).toBeInTheDocument();
  expect(screen.getByText('Doc 1999')).toBeInTheDocument();
  expect(screen.queryByText('Doc 2000')).toBeNull();
});

test('hashChangeDoesNotBreakHookOrder', async () => {
  location.hash = '';
  const errors = vi.spyOn(console, 'error').mockImplementation(() => {});
  mockFetch(unauthorized);
  renderWith({ title: 'Titular', subtitle: 'Sub', note: 'Nota' });
  expect(await screen.findByRole('heading', { name: 'Entra en el panel' })).toBeInTheDocument();
  location.hash = '#cover';
  act(() => { window.dispatchEvent(new HashChangeEvent('hashchange')); });
  expect(await screen.findByRole('button', { name: 'Empezar la demostración' })).toBeInTheDocument();
  location.hash = '';
  act(() => { window.dispatchEvent(new HashChangeEvent('hashchange')); });
  expect(await screen.findByRole('heading', { name: 'Entra en el panel' })).toBeInTheDocument();
  expect(errors).not.toHaveBeenCalled();
  errors.mockRestore();
});
