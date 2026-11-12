import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Review } from './Review';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

const card = (n: number, authorId = 'u1') => ({
  id: `k${n}`, companyId: 'c1', projectId: 'p1', title: `Ficha ${n}`, body: `Cuerpo ${n}`, authorId, createdAt: '2026-11-08T12:00:00Z', status: 'PENDING',
});
const members = [{ userId: 'u1', displayName: 'Jorge Salas', roles: ['WORKER'], active: true }];
const projects = [{ id: 'p1', companyId: 'c1', name: 'Garajes Plaza Sol' }];

function show(cards: unknown[], onPendingChanged = vi.fn()) {
  const calls = mockFetch({
    'GET /v1/admin/companies/c1/cards?status=PENDING': () => cards,
    'GET /v1/admin/companies/c1/members': () => members,
    'GET /v1/admin/companies/c1/projects': () => projects,
    'POST /v1/admin/cards/k1/review': () => ({}),
    'POST /v1/admin/cards/k2/review': () => ({}),
  });
  renderWithProviders(<Review companyId="c1" onPendingChanged={onPendingChanged} />);
  return { calls, onPendingChanged };
}
const sent = (calls: { key: string; body: unknown }[], id: string) => calls.find((c) => c.key === `POST /v1/admin/cards/${id}/review`)?.body;

test('showsOriginPendingPillAndNames', async () => {
  show([card(1), card(2, 'u9')]);
  expect(await screen.findByText('Ficha redactada por la IA de Jorge Salas')).toBeInTheDocument();
  expect(screen.getByText('Pendiente de revisión')).toBeInTheDocument();
  expect(screen.getAllByText('Garajes Plaza Sol').length).toBeGreaterThan(0);
  await userEvent.click(screen.getByRole('button', { name: /Ficha 2/ }));
  expect(await screen.findByText('Ficha redactada por la IA de una persona que ya no está en la empresa')).toBeInTheDocument();
});

test('correctAndApproveOnlyAfterEditing', async () => {
  const { calls } = show([card(1)]);
  const correct = await screen.findByRole('button', { name: 'Corregir y aprobar' });
  expect(correct).toBeDisabled();
  expect(screen.queryByText('Has cambiado el texto.')).toBeNull();
  await userEvent.clear(screen.getByLabelText('Texto de la ficha'));
  await userEvent.type(screen.getByLabelText('Texto de la ficha'), 'Mejor');
  expect(correct).toBeEnabled();
  await userEvent.click(correct);
  await waitFor(() => expect(sent(calls, 'k1')).toEqual({ decision: 'CORRECT', correctedBody: 'Mejor' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Ficha corregida y aprobada');
});

test('rejectRequiresAReason', async () => {
  const { calls } = show([card(1)]);
  await userEvent.click(await screen.findByRole('button', { name: 'Rechazar' }));
  const send = screen.getByRole('button', { name: 'Rechazar la ficha' });
  expect(send).toBeDisabled();
  await userEvent.type(screen.getByLabelText('Motivo del rechazo'), 'Dudoso');
  expect(send).toBeEnabled();
  await userEvent.click(send);
  await waitFor(() => expect(sent(calls, 'k1')).toEqual({ decision: 'REJECT', note: 'Dudoso' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Ficha rechazada');
});

test('resolvedCardLeavesTheQueueAndTheCountDrops', async () => {
  const { calls, onPendingChanged } = show([card(1), card(2)]);
  await screen.findByRole('heading', { name: 'Ficha 1' });
  expect(onPendingChanged).toHaveBeenLastCalledWith(2);
  await userEvent.click(screen.getByRole('button', { name: 'Aprobar' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Ficha aprobada');
  expect(sent(calls, 'k1')).toEqual({ decision: 'APPROVE' });
  await waitFor(() => expect(screen.queryByRole('button', { name: /Ficha 1/ })).toBeNull());
  await waitFor(() => expect(onPendingChanged).toHaveBeenLastCalledWith(1));
  expect(screen.getByRole('heading', { name: 'Ficha 2' })).toBeInTheDocument();
});

test('showsTheOriginalAfterEditing', async () => {
  show([card(1)]);
  await userEvent.type(await screen.findByLabelText('Texto de la ficha'), ' extra');
  expect(screen.getByText('Has cambiado el texto.')).toBeInTheDocument();
  expect(screen.queryByRole('note', { name: 'Texto original' })).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Ver el original' }));
  expect(screen.getByRole('note', { name: 'Texto original' })).toHaveTextContent('Cuerpo 1');
});

test('emptyQueueSaysThereIsNothingPending', async () => {
  show([]);
  expect(await screen.findByText('No hay fichas pendientes.')).toBeInTheDocument();
});

test('doubleClickApprovesExactlyOneCard', async () => {
  const { calls } = show([card(1), card(2)]);
  const approve = await screen.findByRole('button', { name: 'Aprobar' });
  fireEvent.click(approve);
  fireEvent.click(approve);
  await screen.findByRole('status');
  fireEvent.click(screen.getByRole('button', { name: 'Aprobar' }));
  await waitFor(() => expect(screen.queryByRole('button', { name: /Ficha 1/ })).toBeNull());
  expect(calls.filter((c) => c.key.startsWith('POST'))).toHaveLength(1);
  expect(screen.getByRole('button', { name: 'Aprobar' })).toBeEnabled();
});

test('countIsZeroAfterResolvingTheLastTwoCards', async () => {
  const { onPendingChanged } = show([card(1), card(2)]);
  await userEvent.click(await screen.findByRole('button', { name: 'Aprobar' }));
  await screen.findByRole('heading', { name: 'Ficha 2' });
  await waitFor(() => expect(screen.getByRole('button', { name: 'Aprobar' })).toBeEnabled());
  await userEvent.click(screen.getByRole('button', { name: 'Aprobar' }));
  await waitFor(() => expect(onPendingChanged).toHaveBeenLastCalledWith(0));
});

test('focusMovesToTheNextCardTitleAfterResolving', async () => {
  show([card(1), card(2)]);
  await userEvent.click(await screen.findByRole('button', { name: 'Aprobar' }));
  expect(await screen.findByRole('heading', { name: 'Ficha 2' })).toHaveFocus();
});

test('focusMovesToTheEmptyStateWhenTheQueueIsDone', async () => {
  show([card(1)]);
  await userEvent.click(await screen.findByRole('button', { name: 'Aprobar' }));
  expect(await screen.findByRole('heading', { name: 'No hay fichas pendientes.' })).toHaveFocus();
});
