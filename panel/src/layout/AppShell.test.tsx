import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppShell } from './AppShell';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

const acme = { id: 'c1', name: 'Acme', code: 'ACM', codeReviewed: true };
const beta = { id: 'c2', name: 'Beta', code: 'BET', codeReviewed: true };
const me = { userId: 'u1', displayName: 'Ana', firstName: 'Ana', personCode: 'AAAA', role: 'GROUP_ADMIN' as const, companies: [acme, beta], scannedDocumentsSupported: false };
const quiet = { pendingDocuments: 0, window: { always: true }, timezone: 'Europe/Madrid' };

async function openAs(link: string, routes: Record<string, () => unknown>) {
  const calls = mockFetch({
    'GET /v1/admin/companies': () => [{ ...acme, capabilities: [] }, { ...beta, capabilities: [] }],
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
