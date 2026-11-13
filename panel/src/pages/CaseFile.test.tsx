import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { CaseFile } from './CaseFile';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const co = '/v1/admin/companies/c1';
const fileUrl = `GET ${co}/cases/k1/file`;
const eventsUrl = `GET ${co}/cases/k1/events`;
const docsUrl = `GET ${co}/documents?offset=0&limit=500`;
const addUrl = `POST ${co}/cases/k1/file/documents`;
const removeUrl = `DELETE ${co}/cases/k1/file/documents/e2`;
const doc = (id: string, title: string, status = 'READY', projectId?: string) => ({ id, title, status, projectId, filename: `${id}.pdf`, format: 'PDF', paginated: true, pageCount: 2, uploadedBy: 'u', uploadedAt: '2026-03-10T10:00:00Z', superseded: false });
const caseDocument = doc('k1', 'Expediente 2024/017', 'READY', 'p1');
const adminDoc = (id: string, title: string, projectId?: string, status = 'READY') => ({ id, title, status, projectId, companyId: 'c1', filename: 'f.pdf', format: 'PDF', paginated: true, attemptCount: 1, pagesWithoutText: [], topics: [], uploadedAt: '2026-03-10T10:00:00Z' });
const routes = (documents: unknown[], more: Routes = {}): Routes => ({
  [fileUrl]: () => ({ caseDocument, documents, pendingDocuments: 0 }),
  [eventsUrl]: () => [{ id: 'v1', documentId: 'e1', action: 'ADDED', actorId: 'u', at: '2026-11-12T09:00:00Z' }, { id: 'v2', documentId: 'e2', action: 'REMOVED', actorId: 'u', at: '2026-11-12T10:00:00Z' }],
  [docsUrl]: () => [adminDoc('e1', 'Informe del perito', 'p1'), adminDoc('g1', 'Contrato marco'), adminDoc('x1', 'Documento de otro proyecto', 'p2'), adminDoc('k1', 'Expediente 2024/017', 'p1'), adminDoc('n1', 'Sin leer aún', 'p1', 'PROCESSING')],
  ...more,
});
const show = (r: Routes) => { const calls = mockFetch(r); renderWithProviders(<CaseFile companyId="c1" caseId="k1" />); return calls; };

test('listsTheFileWithStatusLabels', async () => {
  show(routes([doc('e1', 'Informe del perito'), doc('e2', 'Foto 1', 'PENDING_READER'), doc('e3', 'Foto 2', 'READER_PROCESSING'), doc('e4', 'Correo', 'PROCESSING'), doc('e5', 'Plano', 'RECEIVED'), doc('e6', 'Roto', 'FAILED')]));
  expect(await screen.findByRole('heading', { name: 'Expediente 2024/017' })).toBeInTheDocument();
  const status = (title: string) => within(screen.getByText(title, { selector: 'td' }).closest('tr')!);
  expect(status('Foto 1').getByText('Espera al lector OCR')).toBeInTheDocument();
  expect(status('Foto 2').getByText('Espera al lector OCR')).toBeInTheDocument();
  expect(status('Correo').getByText('En lectura')).toBeInTheDocument();
  expect(status('Plano').getByText('En lectura')).toBeInTheDocument();
  expect(status('Roto').getByText('No se pudo leer')).toBeInTheDocument();
  expect(status('Informe del perito').getByText('Listo')).toBeInTheDocument();
});

test('pickerOffersOnlyProjectAndGeneralDocumentsNotAlreadyInTheFile', async () => {
  const calls = show(routes([doc('e1', 'Informe del perito')]));
  await userEvent.click(await screen.findByRole('button', { name: 'Añadir documentos' }));
  const dialog = screen.getByRole('dialog', { name: 'Añadir documentos' });
  const names = within(dialog).getAllByRole('checkbox').map((c) => c.getAttribute('aria-label'));
  expect(names).toEqual(['Contrato marco', 'Sin leer aún']);
  await userEvent.type(within(dialog).getByLabelText('Buscar por título'), 'contrato');
  expect(within(dialog).getAllByRole('checkbox')).toHaveLength(1);
  await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Contrato marco' }));
  await userEvent.click(within(dialog).getByRole('button', { name: 'Añadir' }));
  await waitFor(() => expect(calls.find((c) => c.key === addUrl)?.body).toEqual({ documentIds: ['g1'] }));
});

test('removingAsksConfirmation', async () => {
  const calls = show(routes([doc('e2', 'Foto 1')], { [removeUrl]: () => ({ status: 204 }) }));
  await userEvent.click(await screen.findByRole('button', { name: 'Quitar del expediente Foto 1' }));
  expect(calls.some((c) => c.key === removeUrl)).toBe(false);
  const dialog = screen.getByRole('dialog', { name: /Quitar «Foto 1» del expediente/ });
  await userEvent.click(within(dialog).getByRole('button', { name: 'Quitar' }));
  await waitFor(() => expect(calls.some((c) => c.key === removeUrl)).toBe(true));
});

test('showsTheHistory', async () => {
  show(routes([doc('e1', 'Informe del perito'), doc('e2', 'Foto 1')]));
  const history = (await screen.findByRole('heading', { name: 'Historial' })).closest('section')!;
  expect(within(history).getByText(/Añadido: Informe del perito/)).toBeInTheDocument();
  expect(within(history).getByText(/Quitado: Foto 1/)).toBeInTheDocument();
});
