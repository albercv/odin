import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Keys } from './Keys';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

const list = 'GET /v1/admin/companies/c1/members/u1/keys';
const keys = [
  { id: 'k1', label: 'portátil', prefix: 'odin_ab', createdAt: '2026-11-10T10:00:00Z' },
  { id: 'k2', label: 'vieja', prefix: 'odin_cd', createdAt: '2026-11-01T10:00:00Z', revokedAt: '2026-11-02T10:00:00Z' },
];

test('explainsWhatAKeyGivesAccessTo', async () => {
  mockFetch({ [list]: () => keys });
  renderWithProviders(<Keys companyId="c1" userId="u1" memberName="Ana Gil" />);
  expect(await screen.findByText('Cada clave da acceso a lo mismo que ve Ana Gil en esta empresa.')).toBeInTheDocument();
});

test('revokeButtonSaysRevokeKeyAndNamesItForScreenReaders', async () => {
  const calls = mockFetch({ [list]: () => keys, 'DELETE /v1/admin/keys/k1': () => ({ status: 204 }) });
  renderWithProviders(<Keys companyId="c1" userId="u1" memberName="Ana Gil" />);
  const revoke = await screen.findByRole('button', { name: 'Revocar clave portátil' });
  expect(revoke).toHaveTextContent(/^Revocar clave$/);
  expect(screen.getAllByRole('button', { name: /^Revocar clave/ })).toHaveLength(1);
  await userEvent.click(revoke);
  await waitFor(() => expect(calls.some((c) => c.key === 'DELETE /v1/admin/keys/k1')).toBe(true));
});
