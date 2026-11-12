import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, vi } from 'vitest';
import { Gaps } from './Gaps';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

beforeEach(() => vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-15T10:00:00') }));
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

const route = 'GET /v1/admin/companies/c1/gaps?month=2026-11';
const gap = (text: string, kind: string, times: number, lastAskedOn: string) => ({ text, kind, times, lastAskedOn });
const show = (body: unknown) => {
  mockFetch({ [route]: () => body });
  renderWithProviders(<Gaps companyId="c1" />);
};
const NOTE = 'Consultas que hizo la IA de un trabajador y que no encontraron nada en los documentos. Pueden indicar documentación que falta.';

test('listsGapsWithoutAuthor', async () => {
  show({ withheld: false, gaps: [gap('goteras en la azotea', 'SEARCH', 3, '2026-11-08')] });
  const row = (await screen.findByText('goteras en la azotea')).closest('tr')!;
  expect(within(row).getByText('3')).toBeInTheDocument();
  expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Consulta', 'Tipo', 'Veces', 'Última vez']);
  expect(screen.getByText(NOTE)).toBeInTheDocument();
});

test('lastTimeIsTheDayWithoutHour', async () => {
  show({ withheld: false, gaps: [gap('humedad', 'SEARCH', 1, '2026-11-08')] });
  const row = (await screen.findByText('humedad')).closest('tr')!;
  expect(within(row).getByText('8 de noviembre de 2026')).toBeInTheDocument();
  expect(row.textContent).not.toMatch(/\d:\d\d/);
});

test('emptyStateWithoutGaps', async () => {
  show({ withheld: false, gaps: [] });
  expect(await screen.findByRole('heading', { name: 'Este mes no hay consultas sin resultados' })).toBeInTheDocument();
  expect(screen.queryByRole('table')).toBeNull();
});

test('showsKindLabels', async () => {
  show({ withheld: false, gaps: [gap('uno', 'SEARCH', 1, '2026-11-08'), gap('dos', 'SIMILAR_CASES', 2, '2026-11-07')] });
  expect(within((await screen.findByText('uno')).closest('tr')!).getByText('Búsqueda')).toBeInTheDocument();
  expect(within(screen.getByText('dos').closest('tr')!).getByText('Casos parecidos')).toBeInTheDocument();
});

test('withheldShowsTheProtectionTextInsteadOfTheTable', async () => {
  show({ withheld: true, reason: 'FEW_PEOPLE', gaps: [] });
  expect(await screen.findByText('No se muestra para proteger a las personas (pocas en este periodo)')).toBeInTheDocument();
  expect(screen.queryByRole('table')).toBeNull();
  expect(screen.queryByRole('heading', { name: 'Este mes no hay consultas sin resultados' })).toBeNull();
});

test('changingMonthAsksForThatMonth', async () => {
  mockFetch({ [route]: () => ({ withheld: false, gaps: [] }), 'GET /v1/admin/companies/c1/gaps?month=2026-09': () => ({ withheld: false, gaps: [gap('antigua', 'SEARCH', 1, '2026-09-02')] }) });
  renderWithProviders(<Gaps companyId="c1" />);
  await userEvent.selectOptions(await screen.findByLabelText('Mes'), 'septiembre de 2026');
  expect(await screen.findByText('antigua')).toBeInTheDocument();
});
