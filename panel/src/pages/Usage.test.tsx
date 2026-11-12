import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, vi } from 'vitest';
import { Usage } from './Usage';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

beforeEach(() => vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-15T10:00:00') }));
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

const stats = 'GET /v1/admin/companies/c1/usage-stats?month=2026-11';
const volume = 'GET /v1/admin/companies/c1/delivered-volume?month=2026-11';
const person = (displayName: string, questions: number, cardsSaved: number, memberCode?: string) => ({ userId: `u-${displayName}`, displayName, memberCode, questions, cardsSaved });
const company = {
  questions: { search: 5, similarCases: 2 }, questionsWithoutResults: 1,
  cards: { saved: 3, pending: 1, approved: 1, corrected: 1, rejected: 0, withheld: false },
  documents: { uploaded: 4, ready: 3, failed: 1, waitingForReader: 0 }, tagging: { tagged: 2, pending: 1 },
};
const people = [person('Zoe Gil', 90, 7, '7F3K-E2D'), person('Ana Pérez', 1, 0, 'K2M9-E2D'), { displayName: 'Personas dadas de baja', questions: 4, cardsSaved: 1 }];
const totals = [{ tool: 'DESCRIBE_ME', calls: 2, bytes: 300 }, { tool: 'SEARCH', calls: 12, bytes: 4500 }, { tool: 'SIMILAR_CASES', calls: 0, bytes: 0 }, { tool: 'READ_PAGE', calls: 1, bytes: 90 },
  { tool: 'LIST_DOCUMENTS', calls: 1, bytes: 10 }, { tool: 'VERIFY_CITATION', calls: 1, bytes: 11 }, { tool: 'SAVE_CARD', calls: 1, bytes: 12 }, { tool: 'MY_CARDS', calls: 1, bytes: 13 }, { tool: 'MY_QUESTIONS', calls: 1, bytes: 14 }];

const show = (more: Routes = {}, usage: unknown = { month: '2026-11', company, people }) => {
  const calls = mockFetch({ [stats]: () => usage, [volume]: () => ({ month: '2026-11', totals }), ...more });
  renderWithProviders(<Usage companyId="c1" />);
  return calls;
};
const peopleSection = async () => within(await screen.findByRole('region', { name: 'Por persona' }));
const rowOf = (scope: ReturnType<typeof within>, text: string) => within(scope.getByText(text).closest('tr')!);

test('showsCompanyCounts', async () => {
  show();
  const section = within(await screen.findByRole('region', { name: 'Empresa' }));
  const expected: [string, string][] = [['Preguntas de búsqueda', '5'], ['Preguntas de casos parecidos', '2'], ['Preguntas sin resultados', '1'], ['Fichas aportadas', '3'],
    ['Fichas pendientes de revisión', '1'], ['Fichas aprobadas', '1'], ['Fichas corregidas', '1'], ['Fichas rechazadas', '0'], ['Documentos subidos', '4'], ['Documentos listos', '3'],
    ['Documentos que fallaron', '1'], ['Documentos esperando al lector', '0'], ['Documentos etiquetados', '2'], ['Documentos pendientes de etiquetar', '1']];
  for (const [label, value] of expected) expect(rowOf(section, label).getByText(value)).toBeInTheDocument();
});

test('peopleTableKeepsApiOrder', async () => {
  show();
  const section = await peopleSection();
  await section.findByText('Zoe Gil');
  const names = section.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0]?.textContent);
  expect(names).toEqual(['Zoe Gil', 'Ana Pérez', 'Personas dadas de baja']);
});

test('peopleTableHasNoSortControls', async () => {
  show();
  const section = await peopleSection();
  await section.findByText('Zoe Gil');
  expect(section.queryAllByRole('button')).toHaveLength(0);
  section.getAllByRole('columnheader').forEach((h) => { expect(h).not.toHaveAttribute('aria-sort'); expect(within(h).queryByRole('button')).toBeNull(); });
  expect(section.getAllByRole('row')).toHaveLength(1 + people.length);
});

test('peopleTableShowsNameAndMemberCode', async () => {
  show();
  const section = await peopleSection();
  await section.findByText('Zoe Gil');
  expect(section.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Persona', 'Código', 'Preguntas', 'Fichas aportadas']);
  const zoe = rowOf(section, 'Zoe Gil');
  expect(zoe.getByText('7F3K-E2D')).toBeInTheDocument();
  expect(zoe.getByText('90')).toBeInTheDocument();
  expect(zoe.getByText('7')).toBeInTheDocument();
});

test('departedRowHasNoCode', async () => {
  show();
  const section = await peopleSection();
  const departed = rowOf(section, 'Personas dadas de baja');
  expect(departed.getAllByRole('cell')[1]).toBeEmptyDOMElement();
  expect(departed.getByText('4')).toBeInTheDocument();
});

test('departedRowMayBeMissing', async () => {
  show({}, { month: '2026-11', company, people: [people[0]] });
  const section = await peopleSection();
  await section.findByText('Zoe Gil');
  expect(section.queryByText('Personas dadas de baja')).toBeNull();
});

test('showsDescriptiveNoteWithoutInternalReferences', async () => {
  const { container } = renderAndWait();
  expect(await screen.findByText('Recuentos descriptivos. No sirven para valorar a nadie.')).toBeInTheDocument();
  await screen.findByText('Zoe Gil');
  expect(container.textContent).not.toMatch(/D-0\d\d/);
});

function renderAndWait() {
  mockFetch({ [stats]: () => ({ month: '2026-11', company, people }), [volume]: () => ({ month: '2026-11', totals }) });
  return renderWithProviders(<Usage companyId="c1" />);
}

test('showsDeliveredVolumeWithFixedText', async () => {
  show();
  const section = within(await screen.findByRole('region', { name: 'Volumen entregado a la IA del trabajador' }));
  expect(await section.findByText('Odín no usa ninguna IA externa. La IA de cada trabajador es suya, y Odín no ve lo que gasta. Esto es el volumen de texto que Odín le entrega: no son tokens ni un coste.')).toBeInTheDocument();
  expect(section.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Herramienta', 'Llamadas', 'Bytes']);
  const names = section.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0]?.textContent);
  expect(names).toEqual(['Perfil', 'Búsqueda', 'Casos parecidos', 'Leer página', 'Listar documentos', 'Verificar cita', 'Guardar ficha', 'Mis fichas', 'Mis preguntas']);
  const search = rowOf(section, 'Búsqueda');
  expect(search.getByText('12')).toBeInTheDocument();
  expect(search.getByText('4500')).toBeInTheDocument();
});

test('neverLabelsItConsumptionOrSpending', async () => {
  show();
  await screen.findByText('Zoe Gil');
  await screen.findByText('Búsqueda');
  const labels = [...screen.getAllByRole('heading'), ...screen.getAllByRole('columnheader')].map((e) => e.textContent ?? '');
  expect(labels.length).toBeGreaterThan(6);
  labels.forEach((label) => expect(label).not.toMatch(/consumo|gasto/i));
});

test('changingMonthReloads', async () => {
  const calls = show({
    'GET /v1/admin/companies/c1/usage-stats?month=2026-10': () => ({ month: '2026-10', company, people: [person('Bea Ruiz', 3, 1, 'BBBB-E2D')] }),
    'GET /v1/admin/companies/c1/delivered-volume?month=2026-10': () => ({ month: '2026-10', totals }),
  });
  const month = await screen.findByLabelText('Mes');
  expect(within(month).getAllByRole('option')).toHaveLength(12);
  expect(within(month).getAllByRole('option')[0]).toHaveTextContent('noviembre de 2026');
  await userEvent.selectOptions(month, 'octubre de 2026');
  expect(await screen.findByText('Bea Ruiz')).toBeInTheDocument();
  await waitFor(() => expect(calls.some((c) => c.key.endsWith('delivered-volume?month=2026-10'))).toBe(true));
});

test('withheldCardBreakdownSaysSoAndShowsNoNumbers', async () => {
  show({}, { month: '2026-11', company: { ...company, cards: { saved: 2, pending: 1, withheld: true, reason: 'FEW_PEOPLE' } }, people: [] });
  const section = within(await screen.findByRole('region', { name: 'Empresa' }));
  expect(await section.findByText('No se muestra para proteger a las personas (pocas en este periodo)')).toBeInTheDocument();
  expect(rowOf(section, 'Fichas aportadas').getByText('2')).toBeInTheDocument();
  expect(section.queryByText('Fichas rechazadas')).toBeNull();
});
