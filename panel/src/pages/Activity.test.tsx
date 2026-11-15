import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { Activity } from './Activity';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

type Me = Parameters<typeof Activity>[0]['me'];

const list = 'GET /v1/admin/activity';
const integrity = 'GET /v1/admin/activity/integrity';
const settings = 'GET /v1/admin/audit-settings';
const put = 'PUT /v1/admin/audit-settings';
const group: Me = { userId: 'g1', displayName: 'Ada Rey', firstName: 'Ada', lastName: 'Rey', personCode: 'ADAA', role: 'GROUP_ADMIN', companies: [], scannedDocumentsSupported: false };
const company: Me = { ...group, role: 'COMPANY_ADMIN' };
const companies = [{ id: 'c1', name: 'Acme', code: 'ACME', codeReviewed: true }];
const row = (id: number, over = {}) => ({ id, occurredAt: `2026-11-1${id}T10:00:00Z`, actorKind: 'PERSON', actorName: `Persona ${id}`, actorRole: 'COMPANY_ADMIN',
  channel: 'PANEL', action: 'MEMBER_ADDED', targetLabel: `Objetivo ${id}`, outcome: 'DONE', details: { k: id }, ...over });
const okIntegrity = { logEnabled: true, intact: true, headId: 7, headHash: 'abc123', headAt: '2026-11-14T09:00:00Z' };
const keys = 'GET /v1/admin/activity-keys';
const issue = 'POST /v1/admin/activity-keys';
const off = { enabled: false, chatEnabled: false };

const show = (routes: Routes = {}, me: Me = group) => {
  const calls = mockFetch({
    [list]: () => ({ items: [row(2), row(1)], nextCursor: null }), [integrity]: () => okIntegrity, [settings]: () => ({ enabled: true, chatEnabled: false }),
    [keys]: () => ({ chatAvailable: true }), ...routes,
  });
  renderWithProviders(<Activity me={me} companies={companies} />);
  return calls;
};
const lastList = (calls: { key: string }[]) => calls.filter((c) => c.key.startsWith(list + '?') || c.key === list).at(-1)!.key;

test('rowsKeepTheApiOrderNewestFirst', async () => {
  show();
  const rows = await screen.findAllByRole('row', { name: /Persona/ });
  expect(rows[0]).toHaveTextContent('Persona 2');
  expect(rows[1]).toHaveTextContent('Persona 1');
  expect(rows[0]).toHaveTextContent('Alta de persona');
  expect(rows[0]).toHaveTextContent('Hecho');
});

test('systemActorsAndUnknownActionsFallBack', async () => {
  show({ [list]: () => ({ items: [row(3, { actorKind: 'SYSTEM', actorName: null, action: 'NEW_THING', outcome: 'DENIED', targetLabel: null, targetKind: 'X', targetId: '9' })] }) });
  const r = await screen.findByRole('row', { name: /Sistema/ });
  expect(r).toHaveTextContent('NEW_THING');
  expect(r).toHaveTextContent('Negado');
  expect(r).toHaveTextContent('X 9');
});

test('filtersGoToTheQueryString', async () => {
  const calls = show();
  await screen.findAllByRole('row', { name: /Persona/ });
  fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-11-01' } });
  await userEvent.selectOptions(screen.getByLabelText('Resultado'), 'DENIED');
  await userEvent.selectOptions(screen.getByLabelText('Canal'), 'CHAT');
  await userEvent.selectOptions(screen.getByLabelText('Empresa'), 'c1');
  await waitFor(() => {
    const q = new URL(lastList(calls), 'http://x').searchParams;
    expect(q.get('outcome')).toBe('DENIED');
    expect(q.get('channel')).toBe('CHAT');
    expect(q.get('companyId')).toBe('c1');
    expect(q.get('from')).toMatch(/^2026-11-01/);
  });
});

test('loadMoreAppendsWithTheCursor', async () => {
  const calls = show({ [list]: () => ({ items: [row(2)], nextCursor: 'CUR' }), [`${list}?cursor=CUR`]: () => ({ items: [row(1)], nextCursor: null }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Cargar más' }));
  await screen.findByRole('row', { name: /Persona 1/ });
  expect(screen.getAllByRole('row', { name: /Persona/ })).toHaveLength(2);
  expect(lastList(calls)).toContain('cursor=CUR');
  expect(screen.queryByRole('button', { name: 'Cargar más' })).toBeNull();
});

test('csvLinkCarriesTheFilters', async () => {
  show();
  await screen.findAllByRole('row', { name: /Persona/ });
  await userEvent.selectOptions(screen.getByLabelText('Resultado'), 'FAILED');
  const link = screen.getByRole('link', { name: 'Exportar CSV' });
  expect(link).toHaveAttribute('download');
  expect(link.getAttribute('href')).toBe('/v1/admin/activity.csv?outcome=FAILED');
});

test('detailsExpandAsJson', async () => {
  show();
  const r = await screen.findByRole('row', { name: /Persona 2/ });
  expect(within(r).getByText(/"k": 2/)).toBeInTheDocument();
});

test('offNoticeWhenTheLogIsDisabled', async () => {
  show({ [integrity]: () => ({ ...okIntegrity, logEnabled: false }) });
  expect(await screen.findByText('El registro está apagado: no se anota ninguna acción.')).toBeInTheDocument();
});

test('intactChainAndHeadSeal', async () => {
  show();
  expect(await screen.findByText('Registro íntegro')).toBeInTheDocument();
  expect(screen.getByText(/abc123/)).toBeInTheDocument();
});

test('brokenChainNamesTheEntry', async () => {
  show({ [integrity]: () => ({ ...okIntegrity, intact: false, firstBrokenId: 5 }) });
  expect(await screen.findByText('Cadena rota en la entrada 5')).toBeInTheDocument();
});

test('copySealPutsIdHashAndDateOnTheClipboard', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  show();
  await userEvent.click(await screen.findByRole('button', { name: 'Copiar sello' }));
  expect(writeText).toHaveBeenCalledWith('7 abc123 2026-11-14T09:00:00Z');
});

test('sealCheckShowsTheAnswer', async () => {
  const calls = show({ 'POST /v1/admin/activity/seal-check': () => ({ matches: true }) });
  await userEvent.type(await screen.findByLabelText('Id del sello'), '7');
  await userEvent.type(screen.getByLabelText('Huella del sello'), 'abc123');
  await userEvent.click(screen.getByRole('button', { name: 'Comprobar' }));
  expect(await screen.findByText('El sello coincide: la entrada sigue igual y la cadena hasta ella es íntegra.')).toBeInTheDocument();
  expect(calls.find((c) => c.key.includes('seal-check'))?.body).toEqual({ id: 7, hash: 'abc123' });
});

test('switchOnlyForTheGroupAdmin', async () => {
  show({}, company);
  await screen.findAllByRole('row', { name: /Persona/ });
  expect(screen.queryByRole('button', { name: /Apagar el registro|Encender el registro/ })).toBeNull();
  expect(screen.queryByText('Interruptor del registro')).toBeNull();
});

test('groupAdminSeesTheSwitch', async () => {
  show();
  expect(await screen.findByRole('button', { name: 'Apagar el registro' })).toBeInTheDocument();
});

test('enablingNeedsReferenceAndPasswordAndShowsTheWrongPassword', async () => {
  const calls = show({ [settings]: () => off, [put]: () => ({ status: 403, body: { detail: 'La contraseña no es correcta' } }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Encender el registro' }));
  expect(screen.getByLabelText('Referencia de la política de uso publicada')).toBeRequired();
  expect(screen.getByLabelText('Contraseña actual')).toBeRequired();
  await userEvent.type(screen.getByLabelText('Referencia de la política de uso publicada'), 'intranet/politica');
  await userEvent.type(screen.getByLabelText('Contraseña actual'), 'mala');
  await userEvent.click(screen.getByRole('button', { name: 'Encender' }));
  expect(await screen.findByText('La contraseña actual no es correcta.')).toBeInTheDocument();
  expect(calls.find((c) => c.key === put)?.body).toEqual({ enabled: true, chatEnabled: false, usagePolicyReference: 'intranet/politica', password: 'mala' });
});

test('disablingAsksOnlyForThePassword', async () => {
  const calls = show({ [put]: () => ({ enabled: false, chatEnabled: false }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Apagar el registro' }));
  expect(screen.queryByLabelText('Referencia de la política de uso publicada')).toBeNull();
  await userEvent.type(screen.getByLabelText('Contraseña actual'), 'buena');
  await userEvent.click(screen.getByRole('button', { name: 'Apagar' }));
  await waitFor(() => expect(calls.find((c) => c.key === put)?.body).toEqual({ enabled: false, chatEnabled: false, password: 'buena' }));
});

test('noSortControls', async () => {
  show();
  await screen.findAllByRole('row', { name: /Persona/ });
  expect(screen.queryByRole('button', { name: /ordenar/i })).toBeNull();
  expect(document.querySelector('[aria-sort]')).toBeNull();
  expect(screen.queryByLabelText(/ordenar/i)).toBeNull();
});

const activeKey = { id: 'k1', prefix: 'abcd1234', createdAt: '2026-11-10T10:00:00Z', expiresAt: '2026-12-10T10:00:00Z' };
const TOKEN = 'odin-adm_abcd1234_secretoSecreto';
const askKey = async (password = 'buena') => {
  await userEvent.click(await screen.findByRole('button', { name: 'Emitir clave' }));
  await userEvent.type(screen.getByLabelText('Contraseña actual'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Emitir' }));
};

test('chatDisabledSaysSoAndOffersNoKey', async () => {
  show({ [keys]: () => ({ chatAvailable: false }) });
  expect(await screen.findByText(/La consulta desde el chat está desactivada/)).toBeInTheDocument();
  expect(screen.getByText(/administrador del grupo puede activarla/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Emitir clave' })).toBeNull();
});

test('showsTheActiveKeyWithoutToken', async () => {
  show({ [keys]: () => ({ chatAvailable: true, key: activeKey }) });
  expect(await screen.findByText(/abcd1234/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Revocar' })).toBeInTheDocument();
});

test('issuingShowsTheTokenOnceAndForgetsItOnClose', async () => {
  const calls = show({ [issue]: () => ({ status: 201, body: { id: 'k1', prefix: 'abcd1234', token: TOKEN, expiresAt: '2026-12-10T10:00:00Z' } }) });
  await askKey();
  expect(await screen.findByText(TOKEN)).toBeInTheDocument();
  expect(screen.getByText('Guárdala ahora: no se volverá a mostrar. Caduca en 30 días.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Copiar' })).toBeInTheDocument();
  expect(calls.find((c) => c.key === issue)?.body).toEqual({ password: 'buena' });
  await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }));
  expect(screen.queryByText(TOKEN)).toBeNull();
});

test('copyTokenPutsItOnTheClipboard', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  show({ [issue]: () => ({ status: 201, body: { id: 'k1', prefix: 'abcd1234', token: TOKEN, expiresAt: '2026-12-10T10:00:00Z' } }) });
  await askKey();
  await userEvent.click(await screen.findByRole('button', { name: 'Copiar' }));
  expect(writeText).toHaveBeenCalledWith(TOKEN);
});

test('wrongPasswordAndChatDisabledAreShownInSpanish', async () => {
  show({ [issue]: () => ({ status: 403, body: { detail: 'x' } }) });
  await askKey('mala');
  expect(await screen.findByText('La contraseña actual no es correcta.')).toBeInTheDocument();
});

test('conflictOnIssueExplainsTheChatIsOff', async () => {
  show({ [issue]: () => ({ status: 409, body: { detail: 'x' } }) });
  await askKey();
  expect(await screen.findByText('La consulta desde el chat no está activada.')).toBeInTheDocument();
});

test('revokeAsksForConfirmationAndDeletes', async () => {
  const calls = show({ [keys]: () => ({ chatAvailable: true, key: activeKey }), 'DELETE /v1/admin/activity-keys/k1': () => ({ status: 204 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Revocar' }));
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  await userEvent.click(screen.getByRole('button', { name: 'Revocar la clave' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'DELETE /v1/admin/activity-keys/k1')).toBe(true));
});

test('companyAdminWithSeveralCompaniesPicksOne', async () => {
  const two = [...companies, { id: 'c2', name: 'Beta', code: 'BETA', codeReviewed: true }];
  const calls = mockFetch({ [list]: () => ({ items: [] }), [integrity]: () => okIntegrity, [keys]: () => ({ chatAvailable: true }),
    [issue]: () => ({ status: 201, body: { id: 'k1', prefix: 'abcd1234', token: TOKEN, expiresAt: '2026-12-10T10:00:00Z' } }) });
  renderWithProviders(<Activity me={company} companies={two} />);
  await userEvent.click(await screen.findByRole('button', { name: 'Emitir clave' }));
  await userEvent.selectOptions(screen.getByLabelText('Empresa de la clave'), 'c2');
  await userEvent.type(screen.getByLabelText('Contraseña actual'), 'buena');
  await userEvent.click(screen.getByRole('button', { name: 'Emitir' }));
  await screen.findByText(TOKEN);
  expect(calls.find((c) => c.key === issue)?.body).toEqual({ password: 'buena', companyId: 'c2' });
});

test('groupAdminChatToggleSendsThePasswordAndTheLegalNote', async () => {
  const calls = show({ [put]: () => ({ enabled: true, chatEnabled: true }) });
  expect(await screen.findByText('Lo que se consulta por el chat llega a la IA del administrador, que es un tercero. Actívalo solo tras la validación legal.')).toBeInTheDocument();
  await userEvent.click(await screen.findByRole('button', { name: 'Activar la consulta desde el chat' }));
  await userEvent.type(screen.getByLabelText('Contraseña actual'), 'buena');
  await userEvent.click(screen.getByRole('button', { name: 'Activar' }));
  await waitFor(() => expect(calls.find((c) => c.key === put)?.body).toEqual({ enabled: true, chatEnabled: true, password: 'buena' }));
});

test('switchingTheLogKeepsTheCurrentChatState', async () => {
  const calls = show({ [settings]: () => ({ enabled: true, chatEnabled: true }), [put]: () => ({ enabled: false, chatEnabled: true }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Apagar el registro' }));
  await userEvent.type(screen.getByLabelText('Contraseña actual'), 'buena');
  await userEvent.click(screen.getByRole('button', { name: 'Apagar' }));
  await waitFor(() => expect(calls.find((c) => c.key === put)?.body).toEqual({ enabled: false, chatEnabled: true, password: 'buena' }));
});
