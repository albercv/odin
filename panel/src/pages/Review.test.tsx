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

const base = 'GET /v1/admin/companies/c1/cards?status=PENDING';
// Servidor falso: pagina de 10 en 10 sobre `pending`; aprobar una ficha la quita de la lista.
function show(cards: { id: string; title: string; body: string }[], onPendingChanged = vi.fn()) {
  let pending = cards;
  const page = (offset: number, q = '') => {
    const found = pending.filter((c) => `${c.title} ${c.body}`.includes(q));
    return { cards: found.slice(offset, offset + 10), total: found.length };
  };
  const review = (id: string) => () => { pending = pending.filter((c) => c.id !== id); return {}; };
  const routes: Record<string, () => unknown> = {
    'GET /v1/admin/companies/c1/members': () => members,
    'GET /v1/admin/companies/c1/projects': () => projects,
    [`${base}&offset=0&limit=10`]: () => page(0),
    [`${base}&offset=10&limit=10`]: () => page(10),
    [`${base}&offset=0&limit=10&q=tejado`]: () => page(0, 'tejado'),
  };
  cards.forEach((c) => { routes[`POST /v1/admin/cards/${c.id}/review`] = review(c.id); });
  const calls = mockFetch(routes);
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
  expect(sent(calls, 'k1')).toBeUndefined();
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar corrección' }));
  await waitFor(() => expect(sent(calls, 'k1')).toEqual({ decision: 'CORRECT', correctedBody: 'Mejor' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Ficha corregida y aprobada');
});

const WARNING = 'El autor verá esta nota tal cual. Escribe sobre el contenido de la ficha, no sobre la persona.';

test('rejectDialogWarnsTheAuthorWillSeeTheNote', async () => {
  show([card(1)]);
  await userEvent.click(await screen.findByRole('button', { name: 'Rechazar' }));
  const warning = screen.getByText(WARNING);
  expect(warning.compareDocumentPosition(screen.getByLabelText('Motivo del rechazo')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Rechazar la ficha' })).toBeDisabled();
});

test('correctDialogWarnsTheAuthorWillSeeTheNote', async () => {
  const { calls } = show([card(1)]);
  await userEvent.type(await screen.findByLabelText('Texto de la ficha'), ' extra');
  await userEvent.click(screen.getByRole('button', { name: 'Corregir y aprobar' }));
  const note = screen.getByLabelText('Nota para el autor (opcional)');
  expect(screen.getByText(WARNING).compareDocumentPosition(note) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  await userEvent.type(note, 'Falta la fecha');
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar corrección' }));
  await waitFor(() => expect(sent(calls, 'k1')).toEqual({ decision: 'CORRECT', correctedBody: 'Cuerpo 1 extra', note: 'Falta la fecha' }));
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
  await waitFor(() => expect(onPendingChanged).toHaveBeenLastCalledWith(2));
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

const many = (n: number) => Array.from({ length: n }, (_, i) => card(i + 1));

test('pagesTenCardsAtATime', async () => {
  show(many(15));
  expect(await screen.findByRole('button', { name: /Ficha 10/ })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /Ficha 11/ })).toBeNull();
  expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();
});

test('resolvingTheOnlyCardOfTheLastPageGoesBack', async () => {
  const { onPendingChanged } = show(many(11));
  await screen.findByRole('button', { name: /Ficha 10/ });
  await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }));
  await userEvent.click(await screen.findByRole('button', { name: 'Aprobar' }));
  expect(await screen.findByRole('button', { name: /Ficha 10/ })).toBeInTheDocument();
  expect(screen.queryByRole('navigation', { name: 'Páginas de fichas' })).toBeNull();
  await waitFor(() => expect(onPendingChanged).toHaveBeenLastCalledWith(10));
});

test('badgeDropsFrom15To14', async () => {
  const { onPendingChanged } = show(many(15));
  await screen.findByRole('button', { name: /Ficha 10/ });
  await waitFor(() => expect(onPendingChanged).toHaveBeenLastCalledWith(15));
  await userEvent.click(screen.getByRole('button', { name: 'Aprobar' }));
  await waitFor(() => expect(onPendingChanged).toHaveBeenLastCalledWith(14));
});

test('searchFiltersByCardText', async () => {
  const cards = [...many(12), { ...card(13), body: 'Reparar el tejado' }];
  const { calls } = show(cards);
  await screen.findByRole('button', { name: /Ficha 10/ });
  fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar fichas' }), { target: { value: 'tejado' } });
  expect(await screen.findByRole('button', { name: /Ficha 13/ })).toBeInTheDocument();
  await waitFor(() => expect(screen.queryByRole('button', { name: /Ficha 1$/ })).toBeNull());
  expect(calls.some((c) => c.key.endsWith('&q=tejado'))).toBe(true);
});

test('badgeIsNotCappedAt50', async () => {
  const { onPendingChanged } = show(many(120));
  await screen.findByRole('button', { name: /Ficha 10/ });
  await waitFor(() => expect(onPendingChanged).toHaveBeenLastCalledWith(120));
});
