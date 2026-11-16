import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { IntakeGroups } from './IntakeGroups';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const base = '/v1/admin/companies/c1/intake-batches/b1';
const confirmKey = `POST ${base}/proposals/confirm`;
const obra = { id: 'p1', companyId: 'c1', name: 'Calle Olmo 24', restricted: true, memberCount: 0 };
const sur = { id: 'p2', companyId: 'c1', name: 'Residencial Sur', restricted: true, memberCount: 0 };
const item = (id: string) => ({ id, documentId: `d-${id}`, title: `Doc ${id}`, source: 'RULES', reason: 'motivo' });
const group = (kind: string, confidence: string, projectId: string | undefined, n: number, prefix = 'x') =>
  ({ kind, confidence, projectId, proposals: Array.from({ length: n }, (_, i) => item(`${prefix}${i}`)) });

const show = (groups: unknown[], extra: Routes = {}, projects = [obra, sur]) => {
  const onChanged = vi.fn();
  const calls = mockFetch({ [`GET ${base}/proposals`]: () => ({ groups }), [confirmKey]: () => ({ confirmed: 1 }), ...extra });
  renderWithProviders(<IntakeGroups companyId="c1" batchId="b1" projects={projects} onChanged={onChanged}
    kinds={['PROJECT', 'COMPANY_WIDE']} title="Proyectos" description="d" empty="Nada" />);
  return { calls, onChanged };
};
const open = async () => userEvent.click(await screen.findByRole('button', { name: 'Confirmar todos los grupos seguros' }));
const sent = (calls: { key: string; body: unknown }[]) => calls.filter((c) => c.key === confirmKey).map((c) => (c.body as { proposalIds: string[] }).proposalIds);

test('summaryShowsGroupsDocumentsAndProjects', async () => {
  show([group('PROJECT', 'HIGH', 'p1', 2, 'a'), group('PROJECT', 'HIGH', 'p2', 3, 'b'), group('COMPANY_WIDE', 'HIGH', undefined, 1, 'c')]);
  await open();
  const dialog = screen.getByRole('dialog', { name: 'Confirmar todos los grupos seguros' });
  expect(dialog).toHaveTextContent('Se van a confirmar 3 grupos seguros, con 6 documentos en total.');
  ['Calle Olmo 24 · 2 documentos', 'Residencial Sur · 3 documentos', 'De toda la empresa · 1 documento'].forEach((t) => expect(within(dialog).getByText(t)).toBeInTheDocument());
  expect(dialog).toHaveTextContent('Las propuestas dudosas no se tocan: siguen una a una.');
  expect(within(dialog).getByRole('button', { name: 'Confirmar 3 grupos' })).toBeInTheDocument();
});

test('cancellingConfirmsNothing', async () => {
  const { calls } = show([group('PROJECT', 'HIGH', 'p1', 2)]);
  await open();
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }));
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(sent(calls)).toEqual([]);
});

test('confirmsGroupByGroupInPartsOfFiveHundredAndRefreshes', async () => {
  const { calls, onChanged } = show([group('PROJECT', 'HIGH', 'p1', 501, 'a'), group('PROJECT', 'HIGH', 'p2', 2, 'b')]);
  await open();
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar 2 grupos' }));
  expect(await screen.findByText('Se confirmaron 2 grupos (503 propuestas).')).toBeInTheDocument();
  expect(sent(calls).map((ids) => ids.length)).toEqual([500, 1, 2]);
  expect(sent(calls)[2]).toEqual(['b0', 'b1']);
  expect(onChanged).toHaveBeenCalled();
});

test('lowGroupsAndGroupsWithoutProjectInTheListStayOut', async () => {
  const { calls } = show([group('PROJECT', 'HIGH', 'p1', 2, 'a'), group('PROJECT', 'LOW', 'p2', 4, 'l'), group('PROJECT', 'HIGH', 'p9', 3, 'u')]);
  await open();
  const dialog = screen.getByRole('dialog');
  expect(dialog).toHaveTextContent('Se van a confirmar 1 grupo seguro, con 2 documentos en total.');
  expect(dialog).toHaveTextContent('1 grupo seguro no se puede confirmar todavía: su proyecto no está en la lista de proyectos.');
  await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar 1 grupo' }));
  await screen.findByText('Se confirmaron 1 grupo (2 propuestas).');
  expect(sent(calls)).toEqual([['a0', 'a1']]);
});

test('noButtonWhenThereAreNoSafeGroups', async () => {
  show([group('PROJECT', 'LOW', 'p1', 2), group('PROJECT', 'HIGH', 'p9', 1)]);
  await screen.findByRole('button', { name: 'Confirmar grupo Calle Olmo 24' });
  expect(screen.queryByRole('button', { name: 'Confirmar todos los grupos seguros' })).toBeNull();
});

test('aFailureStopsAndSaysWhatWasConfirmedAndWhichGroupFailed', async () => {
  let n = 0;
  const { calls, onChanged } = show([group('PROJECT', 'HIGH', 'p1', 2, 'a'), group('PROJECT', 'HIGH', 'p2', 3, 'b'), group('COMPANY_WIDE', 'HIGH', undefined, 1, 'c')],
    { [confirmKey]: () => (++n === 2 ? { status: 409, body: { detail: 'Confirma antes el proyecto' } } : { confirmed: 1 }) });
  await open();
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar 3 grupos' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Se confirmaron 1 grupo de 3 (2 propuestas). Falló «Residencial Sur»: Confirma antes el proyecto');
  expect(sent(calls)).toHaveLength(2);
  await waitFor(() => expect(onChanged).toHaveBeenCalled());
});
