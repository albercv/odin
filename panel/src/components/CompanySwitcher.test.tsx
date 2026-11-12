import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { CompanySwitcher } from './CompanySwitcher';
import { AppShell } from '../layout/AppShell';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

const acme = { id: 'c1', name: 'Acme', code: 'ACM', codeReviewed: true };
const beta = { id: 'c2', name: 'Beta', code: 'BET', codeReviewed: true };

test('offersEveryCompanyWithTheCurrentOneSelected', async () => {
  const onChange = vi.fn();
  render(<CompanySwitcher companies={[acme, beta]} currentId="c1" onChange={onChange} />);
  const select = screen.getByRole('combobox', { name: 'Empresa' });
  expect(select).toHaveValue('c1');
  await userEvent.selectOptions(select, 'Beta');
  expect(onChange).toHaveBeenCalledWith('c2');
});

test('switchingLoadsTheOtherCompany', async () => {
  const me = { userId: 'u2', displayName: 'Bea', firstName: 'Bea', personCode: 'BBBB', role: 'COMPANY_ADMIN' as const, companyId: 'c1', companies: [acme, beta] };
  const calls = mockFetch({
    'GET /v1/admin/companies': () => [{ ...acme, capabilities: [] }, { ...beta, capabilities: [] }],
    'GET /v1/admin/companies/c1/cards?status=PENDING': () => [],
    'GET /v1/admin/companies/c2/cards?status=PENDING': () => [],
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c2/projects': () => [],
    'GET /v1/admin/companies/c1/members': () => [],
    'GET /v1/admin/companies/c2/members': () => [],
  });
  renderWithProviders(<AppShell me={me} onLogout={() => {}} onPasswordChanged={() => {}} onProfileChanged={() => {}} />);
  expect(await screen.findByRole('heading', { name: 'Acme' })).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Empresa' }), 'Beta');
  expect(await screen.findByRole('heading', { name: 'Beta' })).toBeInTheDocument();
  await waitFor(() => expect(calls.some((c) => c.key === 'GET /v1/admin/companies/c2/members')).toBe(true));
});

test('choosingACompanyOpensIt', async () => {
  const me = { userId: 'u1', displayName: 'Ana', firstName: 'Ana', personCode: 'AAAA', role: 'GROUP_ADMIN' as const, companies: [acme, beta] };
  const calls = mockFetch({
    'GET /v1/admin/companies': () => [{ ...acme, capabilities: [] }, { ...beta, capabilities: [] }],
    'GET /v1/admin/companies/c2/cards?status=PENDING': () => [],
    'GET /v1/admin/companies/c2/projects': () => [],
    'GET /v1/admin/companies/c2/members': () => [],
  });
  renderWithProviders(<AppShell me={me} onLogout={() => {}} onPasswordChanged={() => {}} onProfileChanged={() => {}} />);
  const select = await screen.findByRole('combobox', { name: 'Empresa' });
  await screen.findByRole('option', { name: 'Beta' });
  await userEvent.selectOptions(select, 'Beta');
  expect(await screen.findByRole('heading', { name: 'Beta' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Empresa' })).toHaveAttribute('aria-current', 'page');
  await waitFor(() => expect(calls.some((c) => c.key === 'GET /v1/admin/companies/c2/members')).toBe(true));
});
