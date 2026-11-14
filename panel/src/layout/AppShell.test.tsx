import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppShell } from './AppShell';
import { currentMonth } from '../components/MonthPicker';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

const acme = { id: 'c1', name: 'Acme', code: 'ACM', codeReviewed: true };
const beta = { id: 'c2', name: 'Beta', code: 'BET', codeReviewed: true };
const me = { userId: 'u1', displayName: 'Ana', firstName: 'Ana', personCode: 'AAAA', role: 'GROUP_ADMIN' as const, companies: [acme, beta], scannedDocumentsSupported: false };
const quiet = { pendingDocuments: 0, window: { always: true }, timezone: 'Europe/Madrid' };

async function openAs(link: string, routes: Record<string, () => unknown>) {
  const calls = mockFetch({
    'GET /v1/admin/companies': () => [{ ...acme, capabilities: [] }, { ...beta, capabilities: [] }],
    'GET /v1/admin/companies/c1/cards?status=PENDING': () => [],
    'GET /v1/admin/companies/c2/cards?status=PENDING': () => [],
    ...routes,
  });
  renderWithProviders(<AppShell me={me} onLogout={() => {}} onPasswordChanged={() => {}} onProfileChanged={() => {}} />);
  await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Empresa' }), 'Beta');
  await userEvent.click(await screen.findByRole('link', { name: link }));
  return calls;
}

test('topicsLivesInsideTheMenuAndUsesTheChosenCompany', async () => {
  const calls = await openAs('Temas', {
    'GET /v1/admin/companies/c2/projects': () => [], 'GET /v1/admin/companies/c2/members': () => [],
    'GET /v1/admin/companies/c2/topics': () => [], 'GET /v1/admin/companies/c2/tagging-status': () => quiet,
  });
  expect(await screen.findByRole('heading', { name: 'Temas', level: 1 })).toBeInTheDocument();
  await waitFor(() => expect(calls.some((c) => c.key === 'GET /v1/admin/companies/c2/topics')).toBe(true));
  expect(calls.some((c) => c.key.includes('/companies/c1/topics'))).toBe(false);
});

test.each([['Uso', 'usage-stats'], ['Huecos', 'gaps']])('%sUsesTheChosenCompany', async (link, route) => {
  const calls = await openAs(link, {
    'GET /v1/admin/companies/c2/projects': () => [], 'GET /v1/admin/companies/c2/members': () => [],
    [`GET /v1/admin/companies/c2/${route}?month=${currentMonth()}`]: () => ({ withheld: false, gaps: [], month: '', company: { questions: { search: 0, similarCases: 0 }, questionsWithoutResults: 0, cards: { saved: 0, pending: 0, withheld: false }, documents: { uploaded: 0, ready: 0, failed: 0, waitingForReader: 0 } }, people: [] }),
    [`GET /v1/admin/companies/c2/delivered-volume?month=${currentMonth()}`]: () => ({ month: '', totals: [] }),
  });
  expect(await screen.findByRole('heading', { name: link, level: 1 })).toBeInTheDocument();
  await waitFor(() => expect(calls.some((c) => c.key.startsWith(`GET /v1/admin/companies/c2/${route}`))).toBe(true));
  expect(calls.some((c) => c.key.includes('/companies/c1/') && !c.key.includes('/cards'))).toBe(false);
});

const shell = () => renderWithProviders(<AppShell me={me} onLogout={() => {}} onPasswordChanged={() => {}} onProfileChanged={() => {}} />);

test('groupAdminLandsOnTheFirstCompanyAndKeepsTheChoiceAcrossTabs', async () => {
  const calls = mockFetch({
    'GET /v1/admin/companies': () => [{ ...acme, capabilities: [] }, { ...beta, capabilities: [] }],
    'GET /v1/admin/companies/c1/cards?status=PENDING': () => [], 'GET /v1/admin/companies/c2/cards?status=PENDING': () => [],
    'GET /v1/admin/companies/c1/projects': () => [], 'GET /v1/admin/companies/c1/members': () => [],
    'GET /v1/admin/companies/c2/projects': () => [], 'GET /v1/admin/companies/c2/members': () => [],
  });
  shell();
  await userEvent.click(await screen.findByRole('link', { name: 'Empresa' }));
  expect(await screen.findByRole('heading', { name: 'Acme' })).toBeInTheDocument();
  expect(screen.getByRole('combobox', { name: 'Empresa' })).toHaveValue('c1');
  expect(screen.queryByText('Elige una empresa en el menú.')).toBeNull();
  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Empresa' }), 'Beta');
  await userEvent.click(screen.getByRole('link', { name: 'Grupo' }));
  await userEvent.click(screen.getByRole('link', { name: 'Empresa' }));
  expect(await screen.findByRole('heading', { name: 'Beta' })).toBeInTheDocument();
  expect(calls.some((c) => c.key === 'GET /v1/admin/companies/c2/members')).toBe(true);
});

test('groupWithoutCompaniesShowsAnEmptyState', async () => {
  mockFetch({ 'GET /v1/admin/companies': () => [] });
  shell();
  await userEvent.click(await screen.findByRole('link', { name: 'Documentos' }));
  expect(await screen.findByRole('heading', { name: 'Todavía no hay empresas' })).toBeInTheDocument();
});
