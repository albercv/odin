import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { Documents } from './Documents';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const list = 'GET /v1/admin/companies/c1/documents?offset=0&limit=500';
const projects = 'GET /v1/admin/companies/c1/projects';
const supersede = 'PUT /v1/admin/documents/d1/superseded-by';
const base = { companyId: 'c1', filename: 'f.pdf', format: 'PDF', paginated: true, attemptCount: 1, uploadedAt: '2026-03-10T10:00:00Z' };
const old = { ...base, id: 'd1', title: 'Póliza 2022', status: 'READY', projectId: 'p1' };
const renewed = { ...base, id: 'd2', title: 'Póliza 2024', status: 'READY', projectId: 'p1', uploadedAt: '2026-05-02T10:00:00Z' };
const draft = { ...base, id: 'd3', title: 'Borrador', status: 'FAILED' };
const marked = { ...old, supersededBy: { id: 'd2', title: 'Póliza 2024' }, supersededAt: '2026-06-01T09:00:00Z' };
const routes = (documents: unknown[], more: Routes = {}): Routes => ({
  [list]: () => documents, [projects]: () => [{ id: 'p1', name: 'Los Almendros' }], ...more,
});

const show = (r: Routes) => {
  const calls = mockFetch(r);
  renderWithProviders(<Documents companyId="c1" />);
  return calls;
};
const openMenu = async (title: string) => {
  const toggle = await screen.findByRole('button', { name: `Más acciones de ${title}` });
  if (toggle.getAttribute('aria-expanded') !== 'true') await userEvent.click(toggle);
};
const openDialog = async (title: string) => {
  await openMenu(title);
  await userEvent.click(screen.getByRole('button', { name: `Marcar como sustituido ${title}` }));
  return screen.getByRole('dialog', { name: 'Marcar como sustituido' });
};

test('showsSupersededMarkWithReplacementTitle', async () => {
  show(routes([marked, renewed]));
  const row = (await screen.findByText('Póliza 2022')).closest('tr')!;
  expect(within(row).getByText('Sustituido')).toBeInTheDocument();
  expect(within(row).getByText('por Póliza 2024')).toBeInTheDocument();
  expect(within(screen.getByText('Póliza 2024', { selector: 'td' }).closest('tr')!).queryByText('Sustituido')).toBeNull();
});

test('candidatesAreReadyDocumentsWithProjectAndDateFilteredByTitle', async () => {
  show(routes([old, renewed, draft]));
  const dialog = await openDialog('Póliza 2022');
  expect(within(dialog).getByText(/Usa esta marca cuando un documento sustituye entero a otro, como una póliza renovada\. Un anexo que cambia una parte no sustituye al contrato\./)).toBeInTheDocument();
  const group = within(dialog).getByRole('group', { name: 'Documento que lo sustituye' });
  const options = within(group).getAllByRole('radio');
  expect(options).toHaveLength(1);
  expect(options[0]).toHaveAccessibleName('Póliza 2024 · Los Almendros · 2 may 2026');
  await userEvent.type(within(dialog).getByLabelText('Buscar por título'), 'zzz');
  expect(within(dialog).getByText('Ningún documento listo coincide con la búsqueda.')).toBeInTheDocument();
  await userEvent.clear(within(dialog).getByLabelText('Buscar por título'));
  await userEvent.type(within(dialog).getByLabelText('Buscar por título'), 'PÓLIZA 20');
  expect(within(dialog).getAllByRole('radio')).toHaveLength(1);
});

test('marksAsSupersededAfterTheConfirmationText', async () => {
  const calls = show(routes([old, renewed], { [supersede]: () => ({ status: 200, body: marked }) }));
  const dialog = await openDialog('Póliza 2022');
  expect(within(dialog).queryByRole('button', { name: 'Marcar como sustituido' })).toBeNull();
  await userEvent.click(within(dialog).getByRole('radio'));
  expect(within(dialog).getByText('“Póliza 2022” quedará guardado, pero Odín lo dará como sustituido por “Póliza 2024” y lo pondrá detrás en las búsquedas.')).toBeInTheDocument();
  await userEvent.click(within(dialog).getByRole('button', { name: 'Marcar como sustituido' }));
  await waitFor(() => expect(calls.find((c) => c.key === supersede)?.body).toEqual({ replacementId: 'd2' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
});

test('conflictMessageStaysInTheOpenDialog', async () => {
  show(routes([old, renewed], { [supersede]: () => ({ status: 409, body: { detail: 'Ese documento ya está sustituido' } }) }));
  const dialog = await openDialog('Póliza 2022');
  await userEvent.click(within(dialog).getByRole('radio'));
  await userEvent.click(within(dialog).getByRole('button', { name: 'Marcar como sustituido' }));
  expect(await within(dialog).findByRole('alert')).toHaveTextContent('Ese documento ya está sustituido');
  expect(screen.getByRole('dialog', { name: 'Marcar como sustituido' })).toBeInTheDocument();
});

test('removesTheMark', async () => {
  const del = 'DELETE /v1/admin/documents/d1/superseded-by';
  const calls = show(routes([marked, renewed], { [del]: () => ({ status: 204 }) }));
  await openMenu('Póliza 2022');
  await userEvent.click(screen.getByRole('button', { name: 'Quitar marca de sustituido de Póliza 2022' }));
  await waitFor(() => expect(calls.some((c) => c.key === del)).toBe(true));
  expect(screen.queryByRole('dialog')).toBeNull();
  await waitFor(() => expect(calls.filter((c) => c.key === list)).toHaveLength(2));
});

test('unmarkedDocumentOffersNoRemoval', async () => {
  show(routes([old, renewed]));
  await openMenu('Póliza 2022');
  expect(screen.queryByRole('button', { name: /Quitar marca/ })).toBeNull();
});

test('detailShowsReplacementAndMarkDate', async () => {
  show(routes([marked, renewed]));
  await openMenu('Póliza 2022');
  await userEvent.click(screen.getByRole('button', { name: 'Ver detalle de Póliza 2022' }));
  const dialog = screen.getByRole('dialog', { name: 'Póliza 2022' });
  expect(within(dialog).getByText('Listo')).toBeInTheDocument();
  expect(within(dialog).getByText('Los Almendros')).toBeInTheDocument();
  expect(within(dialog).getByText('10 mar 2026')).toBeInTheDocument();
  expect(within(dialog).getByText('Sustituido por Póliza 2024')).toBeInTheDocument();
  expect(within(dialog).getByText('Marcado el 1 jun 2026')).toBeInTheDocument();
});
