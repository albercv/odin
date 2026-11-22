import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { DocumentLinks } from './DocumentLinks';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const context = 'GET /v1/admin/documents/d1/context';
const versions = [
  { documentId: 'd0', title: 'Póliza 2019', current: false },
  { documentId: 'd1', title: 'Póliza 2022', current: true },
];
const replaces = { id: 'l1', direction: 'corrects', relation: 'REPLACES', status: 'DETECTED', origin: 'DETECTED', documentId: 'd0', title: 'Póliza 2019', citedIdentifier: 'XYZ-19-11111', evidencePage: 2, evidenceQuote: 'Esta póliza sustituye y anula a la póliza n.º XYZ-19-11111.' };
const modifiedBy = { id: 'l2', direction: 'correctedBy', relation: 'MODIFIES', status: 'DETECTED', origin: 'DETECTED', documentId: 'd5', title: 'Anexo 1', citedIdentifier: 'XYZ-22-22222', evidencePage: 1, evidenceQuote: 'Este anexo modifica la póliza.' };
const confirmed = { ...replaces, id: 'l3', relation: 'RECTIFIES', status: 'CONFIRMED' };

const show = (links: unknown[], more: Routes = {}, changed = vi.fn()) => {
  const calls = mockFetch({ [context]: () => ({ versions, links }), ...more });
  renderWithProviders(<DocumentLinks documentId="d1" onChanged={changed} />);
  return { calls, changed };
};

test('showsTheVersionChainAndTheDetectedLinksWithTheirEvidence', async () => {
  show([replaces, modifiedBy, confirmed]);
  const chain = (await screen.findByRole('heading', { name: 'Versiones' })).closest('section')!;
  const items = within(chain).getAllByRole('listitem');
  expect(items[0]).toHaveTextContent('Póliza 2019');
  expect(items[1]).toHaveTextContent('Póliza 2022');
  expect(items[1]).toHaveTextContent('Vigente');
  const links = screen.getByRole('heading', { name: 'Vínculos detectados' }).closest('section')!;
  expect(within(links).getByText('Sustituye a')).toBeInTheDocument();
  expect(within(links).getByText('Modificado por')).toBeInTheDocument();
  expect(within(links).getByText('Rectifica a')).toBeInTheDocument();
  expect(within(links).getAllByText(/sustituye y anula a la póliza n.º XYZ-19-11111/)[0]).toBeInTheDocument();
  expect(within(links).getAllByText('pág. 2')).toHaveLength(2);
  expect(within(links).getByText('Confirmado')).toBeInTheDocument();
});

test('confirmsASupersessionAfterTheDialog', async () => {
  const { calls, changed } = show([replaces], { 'POST /v1/admin/document-links/l1/confirm': () => ({ status: 204 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Confirmar como sustitución' }));
  expect(calls.some((c) => c.key.includes('/confirm'))).toBe(false);
  await userEvent.click(await screen.findByRole('button', { name: 'Sí, confirmar' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'POST /v1/admin/document-links/l1/confirm')).toBe(true));
  await waitFor(() => expect(changed).toHaveBeenCalled());
});

test('confirmsAModificationWithoutSupersession', async () => {
  const { calls } = show([modifiedBy], { 'POST /v1/admin/document-links/l2/confirm': () => ({ status: 204 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Confirmar' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Sí, confirmar' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'POST /v1/admin/document-links/l2/confirm')).toBe(true));
});

test('dismissesALinkAfterTheDialog', async () => {
  const { calls } = show([replaces], { 'POST /v1/admin/document-links/l1/dismiss': () => ({ status: 204 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Descartar' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Sí, descartar' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'POST /v1/admin/document-links/l1/dismiss')).toBe(true));
});

test('doesNotCallTheApiWhenTheDialogIsCancelled', async () => {
  const { calls } = show([replaces]);
  await userEvent.click(await screen.findByRole('button', { name: 'Descartar' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));
  expect(calls.filter((c) => c.key.startsWith('POST'))).toEqual([]);
});

test('showsTheApiErrorInSpanish', async () => {
  show([replaces], { 'POST /v1/admin/document-links/l1/confirm': () => ({ status: 409, body: { detail: 'Ese documento ya está sustituido, directa o indirectamente, por este.' } }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Confirmar como sustitución' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Sí, confirmar' }));
  expect(await screen.findByText(/ya está sustituido, directa o indirectamente/)).toBeInTheDocument();
});

test('offersNoActionsOnAReviewedLink', async () => {
  show([confirmed, { ...replaces, id: 'l9', status: 'DISMISSED' }]);
  await screen.findByText('Confirmado');
  expect(screen.getByText('Descartado')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Descartar' })).toBeNull();
});
