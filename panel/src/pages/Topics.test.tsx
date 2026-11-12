import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { Topics } from './Topics';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const base = '/v1/admin/companies/c1/topics';
const list = `GET ${base}`;
const status = 'GET /v1/admin/companies/c1/tagging-status';
const agua = { id: 't1', name: 'Agua y humedades', definition: 'Filtraciones y fugas' };
const quiet = { pendingDocuments: 0, window: { always: true }, timezone: 'Europe/Madrid' };

const show = (routes: Routes) => {
  const calls = mockFetch({ [status]: () => quiet, ...routes });
  renderWithProviders(<Topics companyId="c1" />);
  return calls;
};
const sentTo = (calls: { key: string; body: unknown }[], key: string) => calls.find((c) => c.key === key)?.body;
const openActions = async (name: string) => userEvent.click(await screen.findByRole('button', { name: `Más acciones de ${name}` }));

test('listsTopics', async () => {
  show({ [list]: () => [agua] });
  const row = (await screen.findByText('Agua y humedades')).closest('tr')!;
  expect(within(row).getByText('Filtraciones y fugas')).toBeInTheDocument();
  ['Nombre', 'Definición'].forEach((name) => expect(screen.getByRole('columnheader', { name })).toBeInTheDocument());
});

test('addsTopic', async () => {
  const calls = show({ [list]: () => [], [`POST ${base}`]: () => ({ ...agua }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Añadir tema' }));
  const dialog = screen.getByRole('dialog', { name: 'Añadir tema' });
  await userEvent.type(within(dialog).getByLabelText('Nombre'), 'Agua y humedades');
  await userEvent.type(within(dialog).getByLabelText('Definición'), 'Filtraciones y fugas');
  await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar tema' }));
  await waitFor(() => expect(sentTo(calls, `POST ${base}`)).toEqual({ name: 'Agua y humedades', definition: 'Filtraciones y fugas' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
});

test('editsTopic', async () => {
  const calls = show({ [list]: () => [agua], [`PATCH ${base}/t1`]: () => agua });
  await openActions('Agua y humedades');
  await userEvent.click(screen.getByRole('button', { name: 'Editar Agua y humedades' }));
  const dialog = screen.getByRole('dialog', { name: 'Editar tema' });
  expect(within(dialog).getByLabelText('Nombre')).toHaveValue('Agua y humedades');
  await userEvent.clear(within(dialog).getByLabelText('Definición'));
  await userEvent.type(within(dialog).getByLabelText('Definición'), 'Solo fugas');
  await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar tema' }));
  await waitFor(() => expect(sentTo(calls, `PATCH ${base}/t1`)).toEqual({ name: 'Agua y humedades', definition: 'Solo fugas' }));
});

test('deletesAfterConfirmation', async () => {
  const calls = show({ [list]: () => [agua], [`DELETE ${base}/t1`]: () => ({ status: 204 }) });
  await openActions('Agua y humedades');
  await userEvent.click(screen.getByRole('button', { name: 'Borrar Agua y humedades' }));
  const dialog = screen.getByRole('dialog', { name: '¿Borrar el tema Agua y humedades? Dejará de verse en los documentos.' });
  await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  await userEvent.click(screen.getByRole('button', { name: 'Borrar Agua y humedades' }));
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Borrar' }));
  await waitFor(() => expect(calls.some((c) => c.key === `DELETE ${base}/t1`)).toBe(true));
});

test('showsApiErrorInField', async () => {
  show({ [list]: () => [agua], [`POST ${base}`]: () => ({ status: 409, body: { detail: 'Ya hay un tema con ese nombre' } }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Añadir tema' }));
  const dialog = screen.getByRole('dialog', { name: 'Añadir tema' });
  await userEvent.type(within(dialog).getByLabelText('Nombre'), 'Agua y humedades');
  await userEvent.type(within(dialog).getByLabelText('Definición'), 'Otra');
  await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar tema' }));
  const field = (await within(dialog).findByText('Ya hay un tema con ese nombre')).closest('div')!;
  expect(within(field).getByLabelText('Nombre')).toBeInTheDocument();
  expect(screen.getByRole('dialog', { name: 'Añadir tema' })).toBeInTheDocument();
});

test('emptyStateWithoutTopics', async () => {
  show({ [list]: () => [] });
  expect(await screen.findByRole('heading', { name: 'Sin temas no se etiqueta ningún documento' })).toBeInTheDocument();
});

test('noticeAfterChangeAndBacklogReloads', async () => {
  let pending = 0;
  const calls = show({
    [list]: () => [], [`POST ${base}`]: () => { pending = 4; return agua; },
    [status]: () => ({ pendingDocuments: pending, oldestPendingSince: pending ? '2026-11-10T09:00:00Z' : undefined, window: { always: true }, timezone: 'Europe/Madrid' }),
  });
  expect(await screen.findByText('Todos los documentos están etiquetados.')).toBeInTheDocument();
  await userEvent.click(await screen.findByRole('button', { name: 'Añadir tema' }));
  const dialog = screen.getByRole('dialog', { name: 'Añadir tema' });
  await userEvent.type(within(dialog).getByLabelText('Nombre'), 'Agua y humedades');
  await userEvent.type(within(dialog).getByLabelText('Definición'), 'Filtraciones y fugas');
  await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar tema' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Los documentos de esta empresa se volverán a etiquetar');
  expect(await screen.findByText(/4 documentos esperan a ser etiquetados/)).toBeInTheDocument();
  expect(calls.filter((c) => c.key === status).length).toBeGreaterThanOrEqual(2);
});
