import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { DocumentTopics } from './DocumentTopics';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const own = 'GET /v1/admin/documents/d1/topics';
const companyTopics = 'GET /v1/admin/companies/c1/topics';
const water = { topicId: 't1', name: 'Agua y humedades', source: 'AI', state: 'ACTIVE', taggerVersion: 'v1' };
const fire = { topicId: 't2', name: 'Incendios', source: 'ADMIN', state: 'ACTIVE' };
const removed = { topicId: 't3', name: 'Obras', source: 'AI', state: 'REMOVED', decidedByName: 'Ana Pérez', decidedAt: '2026-11-09T10:00:00Z' };
const all = [
  { id: 't1', name: 'Agua y humedades', definition: 'x' }, { id: 't2', name: 'Incendios', definition: 'x' },
  { id: 't3', name: 'Obras', definition: 'x' }, { id: 't4', name: 'Seguros', definition: 'x' },
];
const response = (topics: unknown[]) => ({ topics, tagging: { status: 'DONE', topicListVersion: 1 } });

const show = (topics: unknown[], more: Routes = {}) => {
  const calls = mockFetch({ [own]: () => response(topics), [companyTopics]: () => all, ...more });
  renderWithProviders(<DocumentTopics documentId="d1" companyId="c1" />);
  return calls;
};

test('marksAiTopics', async () => {
  show([water, fire]);
  const ai = (await screen.findByText('Agua y humedades')).closest('li')!;
  expect(within(ai).getByText('IA')).toBeInTheDocument();
  expect(within(ai).getByText('asignado por IA')).toBeInTheDocument();
  expect(within(screen.getByText('Incendios').closest('li')!).queryByText('IA')).toBeNull();
});

test('addsTopic', async () => {
  const calls = show([water], { 'PUT /v1/admin/documents/d1/topics/t4': () => ({ status: 204 }) });
  const select = await screen.findByLabelText('Añadir tema');
  expect(within(select).queryByRole('option', { name: 'Agua y humedades' })).toBeNull();
  await userEvent.selectOptions(select, 'Seguros');
  await userEvent.click(screen.getByRole('button', { name: 'Añadir' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'PUT /v1/admin/documents/d1/topics/t4')).toBe(true));
});

test('removesTopic', async () => {
  const calls = show([water], { 'DELETE /v1/admin/documents/d1/topics/t1': () => ({ status: 204 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Quitar Agua y humedades' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'DELETE /v1/admin/documents/d1/topics/t1')).toBe(true));
});

test('recoversRemovedTopic', async () => {
  const calls = show([water, removed], { 'PUT /v1/admin/documents/d1/topics/t3': () => ({ status: 204 }) });
  const section = (await screen.findByRole('heading', { name: 'Quitados' })).closest('div')!;
  expect(within(section).getByText('Quitado por Ana Pérez')).toBeInTheDocument();
  await userEvent.click(within(section).getByRole('button', { name: 'Recuperar Obras' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'PUT /v1/admin/documents/d1/topics/t3')).toBe(true));
});

test('requestsRetag', async () => {
  const calls = show([water], { 'POST /v1/admin/documents/d1/topics/retag': () => ({ status: 202 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Volver a etiquetar' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'POST /v1/admin/documents/d1/topics/retag')).toBe(true));
  expect(await screen.findByRole('status')).toHaveTextContent('El documento se volverá a etiquetar');
});
