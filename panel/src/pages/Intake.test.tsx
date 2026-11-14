import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, vi } from 'vitest';
import { Intake } from './Intake';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => { vi.restoreAllMocks(); history.replaceState(null, '', '/'); });
beforeEach(() => history.replaceState(null, '', '/?lote=b1'));

const base = '/v1/admin/companies/c1/intake-batches';
const batchRoute = `GET ${base}/b1`;
const batch = (changes = {}) => ({
  id: 'b1', companyId: 'c1', channel: 'COMMAND', state: 'IN_REVIEW', createdAt: '2026-11-13T10:00:00Z',
  uploaded: 40, reading: 2, pending: 12, alreadyThere: 3, discarded: 1, warnings: [], proposed: { PROJECT: 7, SUPERSEDES: 2 }, unplaced: 4, ...changes,
});
const obra = { id: 'p1', companyId: 'c1', name: 'Calle Olmo 24', restricted: true, memberCount: 0 };
const otra = { id: 'p2', companyId: 'c1', name: 'Residencial Sur', restricted: true, memberCount: 0 };
const item = (id: string, title: string, reason: string, extra = {}) => ({ id, documentId: `d-${id}`, title, source: 'RULES', reason, ...extra });
const projectGroup = { kind: 'PROJECT', confidence: 'HIGH', projectId: 'p1', proposals: [item('a', 'Factura 12', 'Calle Olmo 24, cabecera'), item('b', 'Acta 3', 'obra Calle Olmo 24')] };
const pairGroup = { kind: 'SUPERSEDES', confidence: 'HIGH', projectId: 'p1', proposals: [item('c', 'Póliza 2026', 'sustituye a la póliza nº 8')] };
const candidates = { candidates: [{ name: 'Calle Olmo 24', origin: 'IMPORTED', documentCount: 9, state: 'PROPOSED' }] };

const show = (routes: Routes) => {
  const calls = mockFetch({
    [batchRoute]: () => batch(), [`GET /v1/admin/companies/c1/projects`]: () => [obra, otra],
    [`GET ${base}/b1/project-candidates`]: () => candidates, [`GET ${base}/b1/proposals`]: () => ({ groups: [projectGroup, pairGroup] }),
    [`GET ${base}/b1/documents?unplaced=true`]: () => ({ documents: [] }), ...routes,
  });
  renderWithProviders(<Intake companyId="c1" />);
  return calls;
};
const step = (name: string) => userEvent.click(screen.getByRole('button', { name }));
const called = (calls: { key: string }[], key: string) => calls.some((c) => c.key === key);

test('showsFourStepsAndNoUploadStep', async () => {
  show({});
  await screen.findByText('Subidos');
  const steps = within(screen.getByRole('navigation', { name: 'Pasos de la primera carga' })).getAllByRole('button');
  expect(steps.map((s) => s.textContent)).toEqual(['1. Lista de proyectos', '2. Proyectos', '3. Parejas', '4. Fichas y expedientes']);
});

test('showsCountsWithoutPeople', async () => {
  show({});
  const counts = await screen.findByRole('region', { name: 'Recuentos' });
  const value = (label: string) => within(counts).getByText(label).nextElementSibling?.textContent;
  expect([value('Subidos'), value('En lectura'), value('Sin confirmar'), value('Ya estaban'), value('Descartados'), value('Sin ubicar')]).toEqual(['40', '2', '12', '3', '1', '4']);
  expect(value('Proyectos propuestos')).toBe('7');
  expect(value('Versiones propuestas')).toBe('2');
  expect(counts.textContent).not.toMatch(/decidi/i);
});

test('warnsAboutMissingReaderLocalModelAndProjectList', async () => {
  const warnings = [
    { code: 'NO_READER', message: 'No hay lector OCR: los escaneos esperan.' },
    { code: 'NO_LOCAL_MODEL', message: 'No hay modelo local: solo proponen las reglas.' },
    { code: 'PROJECT_LIST_NOT_CONFIRMED', message: 'Falta confirmar la lista de proyectos.' },
  ];
  show({ [batchRoute]: () => batch({ warnings }) });
  const region = await screen.findByRole('region', { name: 'Avisos' });
  warnings.forEach((w) => expect(within(region).getByText(w.message)).toBeInTheDocument());
});

test('warnsThatPendingDocumentsStayHiddenWithoutDeadline', async () => {
  show({});
  const region = await screen.findByRole('region', { name: 'Avisos' });
  expect(region).toHaveTextContent('12 documentos siguen pendientes y ningún trabajador los ve');
  expect(region).toHaveTextContent('«No lo sé» los deja ocultos, sin plazo');
});

test('noWarningsWhenNothingPending', async () => {
  show({ [batchRoute]: () => batch({ pending: 0 }) });
  await screen.findByText('Subidos');
  expect(screen.queryByRole('region', { name: 'Avisos' })).toBeNull();
});

test('withoutBatchOffersToStartOne', async () => {
  history.replaceState(null, '', '/');
  const calls = show({ [`POST ${base}`]: () => batch() });
  await userEvent.click(screen.getByRole('button', { name: 'Empezar una primera carga' }));
  await waitFor(() => expect(calls.find((c) => c.key === `POST ${base}`)?.body).toEqual({ channel: 'PANEL' }));
  expect(await screen.findByText('Subidos')).toBeInTheDocument();
  expect(location.search).toBe('?lote=b1');
});

test('confirmsACandidateAsNewProject', async () => {
  const calls = show({ [`POST ${base}/b1/project-candidates/confirm`]: () => candidates });
  await userEvent.click(await screen.findByRole('button', { name: 'Crear Calle Olmo 24' }));
  await waitFor(() => expect(calls.find((c) => c.key === `POST ${base}/b1/project-candidates/confirm`)?.body).toEqual({ decisions: [{ name: 'Calle Olmo 24', decision: 'CREATE' }] }));
});

test('mergesACandidateIntoAnExistingProject', async () => {
  const calls = show({ [`POST ${base}/b1/project-candidates/confirm`]: () => candidates });
  await userEvent.selectOptions(await screen.findByLabelText('Unir Calle Olmo 24 a'), 'Residencial Sur');
  await userEvent.click(screen.getByRole('button', { name: 'Unir Calle Olmo 24' }));
  await waitFor(() => expect(calls.find((c) => c.key === `POST ${base}/b1/project-candidates/confirm`)?.body).toEqual({ decisions: [{ name: 'Calle Olmo 24', decision: 'MERGE', projectId: 'p2' }] }));
});

test('importsAProjectListAndDiscoversFromTexts', async () => {
  const calls = show({ [`POST ${base}/b1/project-candidates/import`]: () => candidates, [`POST ${base}/b1/project-candidates/discover`]: () => candidates });
  await userEvent.upload(await screen.findByLabelText('Importar la lista de proyectos'), new File(['Calle Olmo 24'], 'lista.csv', { type: 'text/csv' }));
  await waitFor(() => expect(called(calls, `POST ${base}/b1/project-candidates/import`)).toBe(true));
  await userEvent.click(screen.getByRole('button', { name: 'Buscar proyectos en los textos' }));
  await waitFor(() => expect(called(calls, `POST ${base}/b1/project-candidates/discover`)).toBe(true));
});

test('groupExpandsWithDocumentsAndReasonsBeforeConfirming', async () => {
  const calls = show({});
  await step('2. Proyectos');
  expect(await screen.findByText('Calle Olmo 24 · confianza alta · 2 documentos')).toBeInTheDocument();
  expect(screen.queryByText('Calle Olmo 24, cabecera')).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Ver documentos de Calle Olmo 24' }));
  expect(screen.getByText('Factura 12')).toBeInTheDocument();
  expect(screen.getByText('Calle Olmo 24, cabecera')).toBeInTheDocument();
  expect(calls.some((c) => c.key.startsWith('POST'))).toBe(false);
});

test('confirmingAGroupAsksWithTheDialogAndSendsAllIds', async () => {
  const calls = show({ [`POST ${base}/b1/proposals/confirm`]: () => ({ confirmed: 2 }) });
  await step('2. Proyectos');
  await userEvent.click(await screen.findByRole('button', { name: 'Confirmar grupo Calle Olmo 24' }));
  const dialog = screen.getByRole('dialog', { name: '¿Confirmar 2 documentos en Calle Olmo 24? Desde ese momento los verán quienes ven el proyecto.' });
  await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
  expect(called(calls, `POST ${base}/b1/proposals/confirm`)).toBe(false);
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar grupo Calle Olmo 24' }));
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }));
  await waitFor(() => expect(calls.find((c) => c.key === `POST ${base}/b1/proposals/confirm`)?.body).toEqual({ proposalIds: ['a', 'b'] }));
});

const decide = async (button: string, expected: unknown, extra = async () => {}) => {
  const key = `POST ${base}/b1/proposals/a/decide`;
  const calls = show({ [key]: () => ({ id: 'a', state: 'CONFIRMED' }) });
  await step('2. Proyectos');
  await userEvent.click(await screen.findByRole('button', { name: 'Ver documentos de Calle Olmo 24' }));
  await extra();
  await userEvent.click(screen.getByRole('button', { name: button }));
  await waitFor(() => expect(calls.find((c) => c.key === key)?.body).toEqual(expected));
};

test('decidesOneByOneConfirm', () => decide('Confirmar Factura 12', { decision: 'CONFIRM' }));
test('decidesOneByOneCompanyWide', () => decide('De toda la empresa Factura 12', { decision: 'COMPANY_WIDE' }));
test('decidesOneByOneDontKnow', () => decide('No lo sé Factura 12', { decision: 'DONT_KNOW' }));
test('decidesOneByOneReject', () => decide('Rechazar Factura 12', { decision: 'REJECT' }));
test('decidesOneByOneCorrectProject', () => decide('Corregir proyecto de Factura 12', { decision: 'CORRECT_PROJECT', projectId: 'p2' },
  () => userEvent.selectOptions(screen.getByLabelText('Proyecto correcto de Factura 12'), 'Residencial Sur')));

test('unplacedDocumentsCanBePlacedOrDiscarded', async () => {
  const docs = { documents: [{ id: 'u1', title: 'Escaneo suelto', path: 'cliente/varios', status: 'READY' }] };
  const calls = show({
    [`GET ${base}/b1/documents?unplaced=true`]: () => docs,
    [`POST ${base}/b1/documents/u1/place`]: () => ({ status: 204 }), [`POST ${base}/b1/documents/u1/withdraw`]: () => ({}),
  });
  await step('2. Proyectos');
  const section = await screen.findByRole('region', { name: 'Sin ubicar' });
  expect(within(section).getByText('cliente/varios')).toBeInTheDocument();
  await userEvent.click(within(section).getByRole('button', { name: 'De toda la empresa Escaneo suelto' }));
  await waitFor(() => expect(calls.find((c) => c.key === `POST ${base}/b1/documents/u1/place`)?.body).toEqual({ companyWide: true }));
  await userEvent.selectOptions(within(section).getByLabelText('Proyecto de Escaneo suelto'), 'Calle Olmo 24');
  await userEvent.click(within(section).getByRole('button', { name: 'Colocar Escaneo suelto' }));
  await waitFor(() => expect(calls.filter((c) => c.key.endsWith('/place')).at(-1)?.body).toEqual({ projectId: 'p1' }));
  await userEvent.click(within(section).getByRole('button', { name: 'Descartar Escaneo suelto' }));
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Descartar' }));
  await waitFor(() => expect(called(calls, `POST ${base}/b1/documents/u1/withdraw`)).toBe(true));
});

test('pairsStepShowsOnlyPairKinds', async () => {
  show({});
  await step('3. Parejas');
  expect(await screen.findByText('Versiones (Calle Olmo 24) · confianza alta · 1 documento')).toBeInTheDocument();
  expect(screen.queryByText(/Calle Olmo 24 · confianza/)).toBeNull();
});

test('caseStepShowsCaseNumberAndConfirmsLinks', async () => {
  const group = { kind: 'CASE_LINK', confidence: 'HIGH', proposals: [item('k', 'Parte 5', 'cita el caso 2024-342', { caseNumber: '2024-342' })] };
  const calls = show({ [`GET ${base}/b1/proposals`]: () => ({ groups: [group] }), [`POST ${base}/b1/proposals/confirm`]: () => ({ confirmed: 1 }) });
  await step('4. Fichas y expedientes');
  await userEvent.click(await screen.findByRole('button', { name: 'Ver documentos de Enlaces a fichas de caso' }));
  expect(screen.getByText('Caso 2024-342')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar grupo Enlaces a fichas de caso' }));
  expect(screen.getByRole('dialog').getAttribute('aria-label')).toBe('¿Confirmar 1 documento en sus fichas de caso? Se añadirán a la ficha con el número que citan.');
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }));
  await waitFor(() => expect(called(calls, `POST ${base}/b1/proposals/confirm`)).toBe(true));
});

test('showsTheApiMessageWhenOrderIsWrong', async () => {
  show({ [`GET ${base}/b1/proposals`]: () => ({ groups: [pairGroup] }), [`POST ${base}/b1/proposals/confirm`]: () => ({ status: 409, body: { detail: 'Confirma antes los proyectos de estos documentos' } }) });
  await step('3. Parejas');
  await userEvent.click(await screen.findByRole('button', { name: 'Confirmar grupo Versiones (Calle Olmo 24)' }));
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Confirma antes los proyectos de estos documentos');
});
