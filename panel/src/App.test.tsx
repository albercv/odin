import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { App } from './App';
import { Documents } from './pages/Documents';
import { Keys } from './components/Keys';
import { mockFetch } from './mockApi';
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
  expect(await screen.findByRole('button', { name: 'Abrir Acme' })).toBeInTheDocument();
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
  expect(screen.queryByRole('link', { name: 'Grupo' })).toBeNull();
});

test('failedIssueKeepsTheLabel', async () => {
  mockFetch({
    'GET /v1/admin/companies/c1/members/u1/keys': () => [],
    'POST /v1/admin/companies/c1/members/u1/keys': () => ({ status: 400, body: { detail: 'Etiqueta no válida' } }),
  });
  render(<Keys companyId="c1" userId="u1" memberName="Ana" />);
  await userEvent.type(await screen.findByLabelText('Etiqueta de la clave'), 'portátil');
  await userEvent.click(screen.getByRole('button', { name: 'Dar clave' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Etiqueta no válida');
  expect(screen.getByLabelText('Etiqueta de la clave')).toHaveValue('portátil');
});

test('issuedKeyShownOnceWithCopy', async () => {
  mockDialog();
  const writeText = vi.fn(async () => {});
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  mockFetch({
    'GET /v1/admin/companies/c1/members/u1/keys': () => [],
    'POST /v1/admin/companies/c1/members/u1/keys': () => ({ status: 201, body: { id: 'k1', prefix: 'odin_ab', key: 'odin_ab.SECRETO' } }),
  });
  render(<Keys companyId="c1" userId="u1" memberName="Ana" />);
  await userEvent.type(await screen.findByLabelText('Etiqueta de la clave'), 'portátil');
  await userEvent.click(screen.getByRole('button', { name: 'Dar clave' }));
  expect(await screen.findByRole('dialog', { name: 'Clave nueva' })).toHaveTextContent('odin_ab.SECRETO');
  expect(screen.getByText(/No se volverá a mostrar/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Copiar' }));
  expect(writeText).toHaveBeenCalledWith('odin_ab.SECRETO');
  await userEvent.click(screen.getByRole('button', { name: 'Entendido' }));
  expect(screen.queryByText('odin_ab.SECRETO')).toBeNull();
});

const page = (documents: unknown[]) => ({ documents, total: documents.length });

test('documentsListShowsStatusAndReason', async () => {
  const calls = mockFetch({
    'GET /v1/admin/companies/c1/documents?offset=0&limit=25': () => page([
      { id: 'd1', title: 'Contrato', filename: 'c.pdf', status: 'FAILED', failureReason: 'Fichero ilegible', pagesWithoutText: [], topics: [] },
      { id: 'd2', title: 'Manual', filename: 'm.txt', status: 'READY', pagesWithoutText: [], topics: [] },
    ]),
    'GET /v1/admin/companies/c1/projects': () => [],
    'POST /v1/admin/documents/d1/retry': () => ({ id: 'd1', status: 'RECEIVED' }),
  });
  render(<Documents companyId="c1" />);
  const row = (await screen.findByText('Contrato')).closest('tr')!;
  expect(within(row).getByText('Fallido')).toBeInTheDocument();
  expect(within(row).getByText('Fichero ilegible')).toBeInTheDocument();
  expect(screen.getByText('Listo')).toBeInTheDocument();
  const retry = within(row).getByRole('button', { name: 'Reintentar Contrato' });
  expect(retry).toHaveTextContent(/^Reintentar$/);
  await userEvent.click(retry);
  await waitFor(() => expect(calls.some((c) => c.key === 'POST /v1/admin/documents/d1/retry')).toBe(true));
});

const docs = (from: number, n: number) =>
  Array.from({ length: n }, (_, i) => ({ id: `d${from + i}`, title: `Doc ${from + i}`, filename: 'f.pdf', status: 'READY', pagesWithoutText: [], topics: [] }));

test('documentsListPagesOnTheServerAndRefreshes', async () => {
  const url = 'GET /v1/admin/companies/c1/documents?offset=0&limit=25';
  const calls = mockFetch({ [url]: () => ({ documents: docs(0, 25), total: 60 }), 'GET /v1/admin/companies/c1/projects': () => [] });
  render(<Documents companyId="c1" />);
  expect(await screen.findByText('Doc 24')).toBeInTheDocument();
  expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
  expect(screen.queryByText(/lista está cortada/)).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Actualizar' }));
  await waitFor(() => expect(calls.filter((c) => c.key === url)).toHaveLength(2));
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

test('caseRouteOpensTheCaseScreenInsideTheShell', async () => {
  history.pushState(null, '', '/casos/k9?empresa=c1');
  mockFetch({
    'GET /v1/admin/me': () => ({ userId: 'u', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1', personCode: 'BEA1', companies: [{ id: 'c1', name: 'Acme', code: 'ACM' }] }),
    'GET /v1/admin/companies/c1/cards?status=PENDING': () => [],
    'GET /v1/admin/companies/c1/cases/k9/file': () => ({ caseDocument: { id: 'k9', title: 'Expte 9', status: 'READY' }, documents: [], pendingDocuments: 0 }),
    'GET /v1/admin/companies/c1/cases/k9/events': () => [],
  });
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Expte 9' })).toBeInTheDocument();
  history.pushState(null, '', '/');
});

test('uploadRouteIsPublicAndNeverAsksForTheSession', async () => {
  history.pushState(null, '', '/subir/tok');
  const calls = mockFetch({ 'GET /v1/upload-links/tok': () => ({ caseTitle: 'Expte', projectName: 'P', expiresAt: '2026-11-14T10:00:00Z' }) });
  render(<App />);
  expect(await screen.findByText('Expte')).toBeInTheDocument();
  expect(calls.some((c) => c.key.includes('/admin/'))).toBe(false);
  history.pushState(null, '', '/');
});

const twoCompanies = {
  'GET /v1/admin/me': () => ({ userId: 'u', displayName: 'Bea', role: 'COMPANY_ADMIN', personCode: 'BEA1', companies: [{ id: 'c1', name: 'Acme', code: 'ACM' }, { id: 'c2', name: 'Beta', code: 'BET' }] }),
  'GET /v1/admin/companies/c1/cards?status=PENDING': () => [],
  'GET /v1/admin/companies/c2/cards?status=PENDING': () => [],
  'GET /v1/admin/companies/c2/cases/k2/file': () => ({ caseDocument: { id: 'k2', title: 'Expte 2', status: 'READY' }, documents: [], pendingDocuments: 0 }),
  'GET /v1/admin/companies/c2/cases/k2/events': () => [],
  'GET /v1/admin/companies/c1/documents?offset=0&limit=25': () => page([]),
  'GET /v1/admin/companies/c1/projects': () => [],
  'GET /v1/admin/companies/c2/documents?offset=0&limit=25': () => page([]),
  'GET /v1/admin/companies/c2/projects': () => [],
};

test('caseLinkOpensUnderTheCompanyOfTheLinkForAnAdminOfTwoCompanies', async () => {
  history.pushState(null, '', '/casos/k2?empresa=c2');
  mockFetch(twoCompanies);
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Expte 2' })).toBeInTheDocument();
  history.pushState(null, '', '/');
});

test('changingCompanyOnTheCaseScreenLeavesTheCase', async () => {
  history.pushState(null, '', '/casos/k2?empresa=c2');
  mockFetch(twoCompanies);
  render(<App />);
  await screen.findByRole('heading', { name: 'Expte 2' });
  await userEvent.selectOptions(screen.getByRole('combobox'), 'c1');
  expect(await screen.findByRole('heading', { name: 'Documentos' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Expte 2' })).toBeNull();
  expect(location.pathname).toBe('/');
});

test('backButtonFollowsTheUrlIntoAndOutOfTheCase', async () => {
  history.pushState(null, '', '/');
  mockFetch(twoCompanies);
  render(<App />);
  await screen.findByRole('link', { name: 'Documentos' });
  act(() => { history.pushState(null, '', '/casos/k2?empresa=c2'); window.dispatchEvent(new PopStateEvent('popstate')); });
  expect(await screen.findByRole('heading', { name: 'Expte 2' })).toBeInTheDocument();
  act(() => { history.pushState(null, '', '/'); window.dispatchEvent(new PopStateEvent('popstate')); });
  await waitFor(() => expect(screen.queryByRole('heading', { name: 'Expte 2' })).toBeNull());
});
