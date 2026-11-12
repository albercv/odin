import { screen } from '@testing-library/react';
import { afterEach, vi } from 'vitest';
import { TaggingBacklog } from './TaggingBacklog';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const hoursAgo = (hours: number) => new Date(Date.now() - hours * 3600_000).toISOString();
const show = (status: unknown) => {
  mockFetch({ 'GET /v1/admin/companies/c1/tagging-status': () => status });
  renderWithProviders(<TaggingBacklog companyId="c1" version={0} />);
};
const night = { from: '22:00', to: '07:00', always: false };

test('showsPendingCountAndOldestDay', async () => {
  show({ pendingDocuments: 3, oldestPendingSince: '2026-11-10T09:00:00Z', window: night, timezone: 'Europe/Madrid' });
  expect(await screen.findByText(/^3 documentos esperan a ser etiquetados\. El más antiguo espera desde el 10 de noviembre de 2026\./)).toBeInTheDocument();
});

test('remindsToKeepMachineOnAfterOneDay', async () => {
  show({ pendingDocuments: 1, oldestPendingSince: hoursAgo(30), window: night, timezone: 'Europe/Madrid' });
  expect(await screen.findByText(/El etiquetado se hace entre las 22:00 y las 07:00: el equipo tiene que estar encendido a esas horas\./)).toBeInTheDocument();
  expect(screen.getByText(/^1 documento espera a ser etiquetado\./)).toBeInTheDocument();
});

test('noReminderBeforeOneDay', async () => {
  show({ pendingDocuments: 1, oldestPendingSince: hoursAgo(5), window: night, timezone: 'Europe/Madrid' });
  await screen.findByText(/espera a ser etiquetado/);
  expect(screen.queryByText(/el equipo tiene que estar encendido/)).toBeNull();
});

test('noReminderWithAlwaysWindow', async () => {
  show({ pendingDocuments: 2, oldestPendingSince: hoursAgo(72), window: { always: true }, timezone: 'Europe/Madrid' });
  await screen.findByText(/esperan a ser etiquetados/);
  expect(screen.queryByText(/el equipo tiene que estar encendido/)).toBeNull();
});

test('saysAllTaggedWithoutPending', async () => {
  show({ pendingDocuments: 0, window: night, timezone: 'Europe/Madrid' });
  expect(await screen.findByText('Todos los documentos están etiquetados.')).toBeInTheDocument();
});
