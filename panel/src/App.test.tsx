import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { App } from './App';
import { Documents } from './pages/Documents';
import { Review } from './pages/Review';
import { Keys } from './components/Keys';
import { mockFetch } from './mockApi';

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
    'GET /v1/admin/me': () => ({ userId: 'u', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1' }),
    'GET /v1/admin/companies': () => [company],
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c1/users': () => [],
  });
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Acme' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Nueva empresa' })).toBeNull();
  expect(screen.queryByLabelText('Buscar conocimiento')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Empresas' })).toBeNull();
});

test('issuedKeyShownOnceWithCopy', async () => {
  const writeText = vi.fn(async () => {});
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  mockFetch({
    'GET /v1/admin/users/u1/keys': () => [],
    'POST /v1/admin/users/u1/keys': () => ({ status: 201, body: { id: 'k1', prefix: 'odin_ab', key: 'odin_ab.SECRETO' } }),
  });
  render(<Keys userId="u1" />);
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
    'GET /v1/admin/companies/c1/documents?limit=100': () => [
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
  await userEvent.type(within(await card(3)).getByLabelText('Motivo (opcional)'), 'Dudoso');
  await userEvent.click(within(await card(3)).getByRole('button', { name: 'Rechazar' }));
  const sent = (id: string) => calls.find((c) => c.key === `POST /v1/admin/cards/${id}/review`)?.body;
  await waitFor(() => expect(sent('k3')).toEqual({ decision: 'REJECT', note: 'Dudoso' }));
  expect(sent('k1')).toEqual({ decision: 'APPROVE' });
  expect(sent('k2')).toEqual({ decision: 'CORRECT', correctedBody: 'Mejor' });
});
