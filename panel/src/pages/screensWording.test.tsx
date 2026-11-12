import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { Gaps } from './Gaps';
import { Topics } from './Topics';
import { Usage } from './Usage';
import { Sidebar } from '../layout/Sidebar';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

beforeEach(() => vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-15T10:00:00') }));
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

const company = { questions: { search: 1, similarCases: 0 }, questionsWithoutResults: 0, cards: { saved: 0, pending: 0, withheld: false, approved: 0, corrected: 0, rejected: 0 }, documents: { uploaded: 0, ready: 0, failed: 0, waitingForReader: 0 } };
const routes = {
  'GET /v1/admin/companies/c1/usage-stats?month=2026-11': () => ({ month: '2026-11', company, people: [{ userId: 'u', displayName: 'Ana Pérez', memberCode: 'AAAA-E2D', questions: 1, cardsSaved: 0 }] }),
  'GET /v1/admin/companies/c1/delivered-volume?month=2026-11': () => ({ month: '2026-11', totals: [{ tool: 'SEARCH', calls: 1, bytes: 10 }] }),
  'GET /v1/admin/companies/c1/gaps?month=2026-11': () => ({ withheld: false, gaps: [{ text: 'humedad', kind: 'SEARCH', times: 1, lastAskedOn: '2026-11-08' }] }),
  'GET /v1/admin/companies/c1/topics': () => [{ id: 't', name: 'Agua', definition: 'Fugas' }],
  'GET /v1/admin/companies/c1/tagging-status': () => ({ pendingDocuments: 0, window: { always: true }, timezone: 'Europe/Madrid' }),
};
const labels = () => [...screen.getAllByRole('heading'), ...screen.getAllByRole('columnheader'), ...screen.queryAllByRole('link')].map((e) => e.textContent ?? '');

test.each([['Uso', <Usage key="u" companyId="c1" />, 'Ana Pérez'], ['Huecos', <Gaps key="g" companyId="c1" />, 'humedad'], ['Temas', <Topics key="t" companyId="c1" />, 'Agua']])(
  '%sHasNoInternalReferencesNorConsumptionLabels', async (_name, page, ready) => {
    mockFetch(routes);
    const { container } = renderWithProviders(page);
    await screen.findByText(ready);
    expect(container.textContent).not.toMatch(/D-\d{3}/);
    labels().forEach((label) => expect(label).not.toMatch(/consumo|gasto/i));
  });

test('menuHasNoConsumptionLabels', () => {
  render(<Sidebar isGroup name="Ana" page="usage" pending={0} setPage={() => {}} onLogout={() => {}} />);
  const links = screen.getAllByRole('link').map((l) => l.textContent ?? '');
  expect(links).toEqual(expect.arrayContaining(['Temas', 'Uso', 'Huecos']));
  links.forEach((label) => expect(label).not.toMatch(/consumo|gasto/i));
});
