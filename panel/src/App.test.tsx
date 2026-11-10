import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { App } from './App';
import { Documents } from './pages/Documents';
import { Review } from './pages/Review';
import { Keys } from './components/Keys';
import { mockFetch, type Routes } from './mockApi';

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

test('groupAdminSeesCompaniesAndCapabilities', async () => {
  mockFetch({
    'GET /v1/admin/me': () => ({ userId: 'u', displayName: 'Ada', role: 'GROUP_ADMIN' }),
    'GET /v1/admin/companies': () => [company],
  });
  render(<App />);
  expect(await screen.findByText('Acme')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Nueva empresa' })).toBeInTheDocument();
  expect(screen.getByLabelText('Buscar conocimiento')).toBeChecked();
  expect(screen.getByLabelText('Guardar fichas')).not.toBeChecked();
});

test('companyAdminGoesToOwnCompanyWithoutCapabilityControls', async () => {
  mockFetch({
    'GET /v1/admin/me': () => ({ userId: 'u', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1', companies: [{ id: 'c1', name: 'Acme' }] }),
    'GET /v1/admin/companies': () => [company],
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c1/members': () => [],
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
  const writeText = vi.fn(async () => {});
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  mockFetch({
    'GET /v1/admin/companies/c1/members/u1/keys': () => [],
    'POST /v1/admin/companies/c1/members/u1/keys': () => ({ status: 201, body: { id: 'k1', prefix: 'odin_ab', key: 'odin_ab.SECRETO' } }),
  });
  render(<Keys companyId="c1" userId="u1" />);
  await userEvent.type(await screen.findByLabelText('Etiqueta de la clave'), 'portátil');
  await userEvent.click(screen.getByRole('button', { name: 'Dar clave' }));
  expect(await screen.findByText('odin_ab.SECRETO')).toBeInTheDocument();
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
  await userEvent.click(within(row).getByRole('button', { name: 'Reintentar' }));
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

test('reviewApproveCorrectReject', async () => {
  const cards = [1, 2, 3].map((n) => ({ id: `k${n}`, title: `Ficha ${n}`, body: `Cuerpo ${n}`, status: 'PENDING' }));
  const calls = mockFetch({
    'GET /v1/admin/companies/c1/cards?status=PENDING': () => cards,
    'POST /v1/admin/cards/k1/review': () => ({}),
    'POST /v1/admin/cards/k2/review': () => ({}),
    'POST /v1/admin/cards/k3/review': () => ({}),
  });
  render(<Review companyId="c1" />);
  const card = async (n: number) => (await screen.findByText(`Ficha ${n}`)).closest('article')!;
  await userEvent.click(within(await card(1)).getByRole('button', { name: 'Aprobar' }));
  const second = await card(2);
  await userEvent.clear(within(second).getByLabelText('Texto corregido'));
  await userEvent.type(within(second).getByLabelText('Texto corregido'), 'Mejor');
  await userEvent.click(within(second).getByRole('button', { name: 'Corregir y aprobar' }));
  expect(within(await card(3)).getByRole('button', { name: 'Rechazar' })).toBeDisabled();
  await userEvent.type(within(await card(3)).getByLabelText('Motivo del rechazo'), 'Dudoso');
  await userEvent.click(within(await card(3)).getByRole('button', { name: 'Rechazar' }));
  const sent = (id: string) => calls.find((c) => c.key === `POST /v1/admin/cards/${id}/review`)?.body;
  await waitFor(() => expect(sent('k3')).toEqual({ decision: 'REJECT', note: 'Dudoso' }));
  expect(sent('k1')).toEqual({ decision: 'APPROVE' });
  expect(sent('k2')).toEqual({ decision: 'CORRECT', correctedBody: 'Mejor' });
});
