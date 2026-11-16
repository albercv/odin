import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { App } from '../App';
import { mockFetch } from '../mockApi';
import { mockDialog } from '../ui/dialogMock';

vi.mock('../brain/BrainCanvas', () => ({ BrainCanvas: () => null }));
mockDialog();

const me = { userId: 'u1', displayName: 'Bea Pérez', firstName: 'Bea', lastName: 'Pérez', role: 'COMPANY_ADMIN', companyId: 'c1', companies: [{ id: 'c1', name: 'Acme' }], expiresAt: '2099-01-01T00:00:00Z', absoluteExpiresAt: '2099-01-01T12:00:00Z' };
const here = { id: 's1', createdAt: '2026-11-16T08:00:00Z', lastSeenAt: '2026-11-16T09:00:00Z', userAgent: 'Firefox', ip: '10.0.0.7', current: true };
const other = { id: 's2', createdAt: '2026-11-15T08:00:00Z', lastSeenAt: '2026-11-15T09:00:00Z', current: false };

function routes() {
  let sessions = [here, other];
  return mockFetch({
    'GET /v1/admin/me': () => me,
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c1/members': () => [],
    'GET /v1/admin/sessions': () => sessions,
    'DELETE /v1/admin/sessions/s2': () => { sessions = [here]; return { status: 204 }; },
  });
}

async function open() {
  render(<App />);
  await userEvent.click(await screen.findByRole('link', { name: 'Mis sesiones' }));
}

test('listsOwnSessionsMarkingTheCurrentOne', async () => {
  routes();
  await open();
  expect(await screen.findByText('Firefox')).toBeInTheDocument();
  expect(screen.getByText('10.0.0.7')).toBeInTheDocument();
  expect(screen.getAllByText('Esta sesión')).toHaveLength(1);
  expect(screen.getAllByRole('button', { name: 'Cerrar' })).toHaveLength(2);
});

test('closingAsksForConfirmationAndReloadsTheList', async () => {
  const calls = routes();
  await open();
  await screen.findByText('Firefox');
  const rows = screen.getAllByRole('row');
  await userEvent.click(within(rows[2]!).getByRole('button', { name: 'Cerrar' }));
  expect(calls.some((c) => c.key === 'DELETE /v1/admin/sessions/s2')).toBe(false);
  await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cerrar' }));
  await vi.waitFor(() => expect(screen.getAllByRole('button', { name: 'Cerrar' })).toHaveLength(1));
  expect(calls.some((c) => c.key === 'DELETE /v1/admin/sessions/s2')).toBe(true);
});

test('cancellingTheConfirmationClosesNothing', async () => {
  const calls = routes();
  await open();
  await screen.findByText('Firefox');
  await userEvent.click(within(screen.getAllByRole('row')[2]!).getByRole('button', { name: 'Cerrar' }));
  await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancelar' }));
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
});
