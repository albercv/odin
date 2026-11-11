import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { Members } from './Members';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

type Me = Parameters<typeof Members>[0]['me'];

afterEach(() => vi.restoreAllMocks());

const ana = { userId: 'u1', displayName: 'Ana', firstName: 'Ana', personCode: 'AAAA', memberCode: 'AAAA-ACM', email: 'ana@acme.test', roles: ['WORKER'], active: true, hasPassword: false };
const bea = { userId: 'u2', displayName: 'Bea', firstName: 'Bea', personCode: 'BBBB', memberCode: 'BBBB-ACM', roles: ['COMPANY_ADMIN'], active: false, email: 'bea@acme.test', hasPassword: true };
const list = 'GET /v1/admin/companies/c1/members';
const groupAdmin: Me = { userId: 'g', displayName: 'Ada', personCode: 'GGGG', role: 'GROUP_ADMIN', companies: [] };
const companyAdmin: Me = { userId: 'u2', displayName: 'Bea', personCode: 'BBBB', role: 'COMPANY_ADMIN', companyId: 'c1', companies: [{ id: 'c1', name: 'Acme', code: 'ACM' }] };

const show = (routes: Routes, me: Me = groupAdmin, onProjectsChanged?: () => void) => {
  const calls = mockFetch(routes);
  renderWithProviders(<Members companyId="c1" me={me} onProjectsChanged={onProjectsChanged} />);
  return calls;
};
const rowOf = async (name: string) =>
  waitFor(() => {
    const row = screen.getAllByRole('row').find((r) => r.textContent?.includes(name));
    if (!row) throw new Error(`sin fila ${name}`);
    return row;
  });
const act = async (person: string, action: string) => {
  const toggle = await screen.findByRole('button', { name: `Más acciones de ${person}` });
  if (toggle.getAttribute('aria-expanded') !== 'true') await userEvent.click(toggle);
  await userEvent.click(screen.getByRole('button', { name: action }));
};

const gil = { userId: 'u5', displayName: 'Cora Gil', firstName: 'Cora', lastName: 'Gil', personCode: '7F3K', memberCode: '7F3K-ACM', email: 'cora@acme.test', roles: ['WORKER'], active: true };
const both = { userId: 'u6', displayName: 'Dani Sol', firstName: 'Dani', lastName: 'Sol', personCode: 'DDDD', memberCode: 'DDDD-ACM', roles: ['COMPANY_ADMIN', 'WORKER'], active: true };
const projectList = 'GET /v1/admin/companies/c1/projects';
const obra = { id: 'p1', companyId: 'c1', name: 'Obra', restricted: true };
const reforma = { id: 'p2', companyId: 'c1', name: 'Reforma', restricted: true };
const sala = { id: 'p3', companyId: 'c1', name: 'Sala', restricted: false };
const patio = { id: 'p4', companyId: 'c1', name: 'Patio', restricted: false };

test('rowShowsMemberCodeRolesAndState', async () => {
  show({ [list]: () => [gil, bea, both] });
  const [gilRow, beaRow, bothRow] = [await rowOf('Cora Gil'), await rowOf('Bea'), await rowOf('Dani Sol')];
  ['7F3K-ACM', 'Cora Gil', 'cora@acme.test', 'Trabaja con su IA', 'Activo'].forEach((t) => expect(gilRow).toHaveTextContent(t));
  ['BBBB-ACM', 'bea@acme.test', 'Administra la empresa', 'Desactivado'].forEach((t) => expect(beaRow).toHaveTextContent(t));
  ['DDDD-ACM', 'sin correo', 'Administra la empresa', 'Trabaja con su IA'].forEach((t) => expect(bothRow).toHaveTextContent(t));
  expect(gilRow).not.toHaveTextContent('Faltan apellidos');
});

test('rowButtonsShowShortTextAndNameTheTarget', async () => {
  show({ [list]: () => [gil, both] });
  await userEvent.click(await screen.findByRole('button', { name: 'Más acciones de Cora Gil' }));
  const expected: [string, string][] = [['Desactivar', 'Desactivar'], ['Editar', 'Editar'], ['Papeles', 'Papeles de'], ['Quitar', 'Quitar'], ['Claves', 'Claves de'], ['Accesos', 'Accesos de']];
  expected.forEach(([visible, name]) => {
    const button = screen.getByRole('button', { name: `${name} Cora Gil` });
    expect(button).toHaveTextContent(new RegExp(`^${visible}$`));
  });
  await userEvent.click(screen.getByRole('button', { name: 'Más acciones de Dani Sol' }));
  expect(screen.getByRole('button', { name: 'Restablecer contraseña de Dani Sol' })).toHaveTextContent(/^Restablecer contraseña$/);
});

test('inlineFormButtonsShowShortTextAndNameTheTarget', async () => {
  show({ [list]: () => [ana] });
  await act('Ana', 'Editar Ana');
  expect(screen.getByRole('button', { name: 'Guardar Ana' })).toHaveTextContent(/^Guardar$/);
  expect(screen.getByRole('button', { name: 'Cancelar Ana' })).toHaveTextContent(/^Cancelar$/);
});

test('cellsCarryTheirColumnLabelForNarrowScreens', async () => {
  show({ [list]: () => [gil] });
  const row = await rowOf('Cora Gil');
  const labels = [...row.querySelectorAll('td')].map((td) => td.getAttribute('data-label'));
  expect(labels).toEqual(['Código', 'Persona', 'Papel', 'Acceso', null]);
});

test('missingLastNameIsFlagged', async () => {
  show({ [list]: () => [ana, gil] });
  expect(await rowOf('Ana')).toHaveTextContent('Faltan apellidos');
  expect(await rowOf('Cora Gil')).not.toHaveTextContent('Faltan apellidos');
});

const patchMember = 'PATCH /v1/admin/companies/c1/members/u1';

test('changesRoles', async () => {
  const calls = show({ [list]: () => [ana], [patchMember]: () => ana });
  await act('Ana', 'Papeles de Ana');
  expect(screen.getByLabelText('Trabaja con su IA')).toBeChecked();
  expect(screen.queryByLabelText('Contraseña')).toBeNull();
  await userEvent.click(screen.getByLabelText('Administra la empresa'));
  const password = screen.getByLabelText('Contraseña');
  expect(password).toBeRequired();
  expect(password).toHaveAttribute('minlength', '12');
  await userEvent.type(password, 'una-clave-larga-1');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar papeles de Ana' }));
  await waitFor(() => expect(calls.find((c) => c.key === patchMember)?.body).toEqual({ roles: ['COMPANY_ADMIN', 'WORKER'], password: 'una-clave-larga-1' }));
});

test('promotingAPersonWhoAlreadyHasAPasswordSendsOnlyTheRoles', async () => {
  const withPassword = { ...ana, hasPassword: true };
  const calls = show({ [list]: () => [withPassword], [patchMember]: () => withPassword });
  await act('Ana', 'Papeles de Ana');
  await userEvent.click(screen.getByLabelText('Administra la empresa'));
  expect(screen.queryByLabelText('Contraseña')).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Guardar papeles de Ana' }));
  await waitFor(() => expect(calls.find((c) => c.key === patchMember)?.body).toEqual({ roles: ['COMPANY_ADMIN', 'WORKER'] }));
});

test('changingOnlyTheWorkerRoleNeverAsksForAPassword', async () => {
  const calls = show({ [list]: () => [ana], [patchMember]: () => ana });
  await act('Ana', 'Papeles de Ana');
  await userEvent.click(screen.getByLabelText('Trabaja con su IA'));
  await userEvent.click(screen.getByLabelText('Administra la empresa'));
  await userEvent.click(screen.getByLabelText('Administra la empresa'));
  expect(screen.queryByLabelText('Contraseña')).toBeNull();
  await userEvent.click(screen.getByLabelText('Trabaja con su IA'));
  await userEvent.click(screen.getByRole('button', { name: 'Guardar papeles de Ana' }));
  await waitFor(() => expect(calls.find((c) => c.key === patchMember)?.body).toEqual({ roles: ['WORKER'] }));
});

test('noRoleErrorIsLinkedToTheCheckboxes', async () => {
  show({ [list]: () => [ana] });
  await act('Ana', 'Papeles de Ana');
  await userEvent.click(screen.getByLabelText('Trabaja con su IA'));
  await userEvent.click(screen.getByRole('button', { name: 'Guardar papeles de Ana' }));
  const alert = await screen.findByRole('alert');
  expect(screen.getByRole('group', { name: 'Papeles' })).toHaveAccessibleDescription(alert.textContent!);
  expect(screen.getByLabelText('Trabaja con su IA')).toBeInvalid();
});

test('companyAdminOnlyChangesTheWorkerRole', async () => {
  show({ [list]: () => [ana] }, companyAdmin);
  await act('Ana', 'Papeles de Ana');
  expect(screen.queryByLabelText('Administra la empresa')).toBeNull();
  expect(screen.getByLabelText('Trabaja con su IA')).toBeInTheDocument();
});

test('deactivatesAndActivates', async () => {
  const calls = show({
    [list]: () => [ana, bea],
    'PATCH /v1/admin/companies/c1/members/u1': () => ({ ...ana, active: false }),
    'PATCH /v1/admin/companies/c1/members/u2': () => ({ ...bea, active: true }),
  });
  await act('Ana', 'Desactivar Ana');
  await waitFor(() => expect(calls.find((c) => c.key === 'PATCH /v1/admin/companies/c1/members/u1')?.body).toEqual({ active: false }));
  await act('Bea', 'Activar Bea');
  await waitFor(() => expect(calls.find((c) => c.key === 'PATCH /v1/admin/companies/c1/members/u2')?.body).toEqual({ active: true }));
});

test('editSavesNameAndEmail', async () => {
  const calls = show({ [list]: () => [ana], 'PATCH /v1/admin/users/u1': () => ana });
  await act('Ana', 'Editar Ana');
  await userEvent.clear(screen.getByLabelText('Nombre'));
  await userEvent.type(screen.getByLabelText('Nombre'), 'Anita');
  await userEvent.clear(screen.getByLabelText('Correo'));
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Ana' }));
  await waitFor(() =>
    expect(calls.find((c) => c.key === 'PATCH /v1/admin/users/u1')?.body).toEqual({ firstName: 'Anita', email: '' }));
});

test('editSendsTheLastNameWhenThereIsOne', async () => {
  const gil = { ...ana, userId: 'u5', displayName: 'Cora Gil', firstName: 'Cora', lastName: 'Gil' };
  const calls = show({ [list]: () => [gil], 'PATCH /v1/admin/users/u5': () => gil });
  await act('Cora Gil', 'Editar Cora Gil');
  expect(screen.getByLabelText('Apellidos')).toHaveValue('Gil');
  await userEvent.clear(screen.getByLabelText('Apellidos'));
  await userEvent.type(screen.getByLabelText('Apellidos'), 'Gil Ruiz');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Cora Gil' }));
  await waitFor(() =>
    expect(calls.find((c) => c.key === 'PATCH /v1/admin/users/u5')?.body)
      .toEqual({ firstName: 'Cora', lastName: 'Gil Ruiz', email: 'ana@acme.test' }));
});

test('clearingTheLastNameIsSentSoTheApiRejectsIt', async () => {
  const gil = { ...ana, userId: 'u5', displayName: 'Cora Gil', firstName: 'Cora', lastName: 'Gil' };
  const calls = show({
    [list]: () => [gil],
    'PATCH /v1/admin/users/u5': () => ({ status: 400, body: { detail: 'Faltan los apellidos' } }),
  });
  await act('Cora Gil', 'Editar Cora Gil');
  await userEvent.clear(screen.getByLabelText('Apellidos'));
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Cora Gil' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Faltan los apellidos');
  expect(calls.find((c) => c.key === 'PATCH /v1/admin/users/u5')?.body).toMatchObject({ lastName: '' });
});

test('conflictShowsTheApiMessage', async () => {
  show({
    [list]: () => [ana],
    'PATCH /v1/admin/users/u1': () => ({ status: 409, body: { detail: 'Ese correo ya existe' } }),
  });
  await act('Ana', 'Editar Ana');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Ana' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Ese correo ya existe');
  expect(screen.getByLabelText('Nombre')).toHaveValue('Ana');
});

test('removeAsksForConfirmation', async () => {
  let users = [ana];
  const calls = show({
    [list]: () => users,
    'DELETE /v1/admin/companies/c1/members/u1': () => { users = []; return { status: 204 }; },
  });
  const message = '¿Quitar a Ana de esta empresa? Sus claves y sus proyectos de esta empresa dejarán de valer.';
  await act('Ana', 'Quitar Ana');
  const dialog = screen.getByRole('dialog', { name: message });
  await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  expect(screen.queryByRole('dialog')).toBeNull();
  await act('Ana', 'Quitar Ana');
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Quitar' }));
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Más acciones de Ana' })).toBeNull());
  expect(calls.some((c) => c.key === 'DELETE /v1/admin/companies/c1/members/u1')).toBe(true);
});

test('removingAPersonAnnouncesItAndKeepsTheFocusInTheList', async () => {
  let users = [ana, gil];
  const onProjectsChanged = vi.fn();
  show({ [list]: () => users, 'DELETE /v1/admin/companies/c1/members/u1': () => { users = [gil]; return { status: 204 }; } }, groupAdmin, onProjectsChanged);
  await act('Ana', 'Quitar Ana');
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Quitar' }));
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Se ha quitado a Ana de esta empresa'));
  await waitFor(() => expect(screen.getByRole('region', { name: 'Lista de personas' })).toHaveFocus());
  expect(onProjectsChanged).toHaveBeenCalled();
});

test('issuedKeyOnlyClosesWithEntendido', async () => {
  show({
    [list]: () => [ana],
    'GET /v1/admin/companies/c1/members/u1/keys': () => [],
    'POST /v1/admin/companies/c1/members/u1/keys': () => ({ status: 201, body: { id: 'k1', prefix: 'odin_ab', key: 'odin_ab.SECRETO' } }),
  });
  await act('Ana', 'Claves de Ana');
  await userEvent.type(await screen.findByLabelText('Etiqueta de la clave'), 'portátil');
  await userEvent.click(screen.getByRole('button', { name: 'Dar clave' }));
  const dialog = await screen.findByRole('dialog', { name: 'Clave nueva' });
  expect(within(dialog).getByText('odin_ab.SECRETO')).toBeInTheDocument();
  expect(within(dialog).getByText(/No se volverá a mostrar/)).toBeInTheDocument();
  expect(within(dialog).queryByRole('button', { name: 'Cerrar' })).toBeNull();
  fireEvent(dialog, new Event('cancel', { cancelable: true }));
  expect(screen.getByRole('dialog', { name: 'Clave nueva' })).toBeInTheDocument();
  await userEvent.click(within(dialog).getByRole('button', { name: 'Entendido' }));
  expect(screen.queryByText('odin_ab.SECRETO')).toBeNull();
});

test('secondKeyShowsInAnOpenDialog', async () => {
  let n = 0;
  show({
    [list]: () => [ana],
    'GET /v1/admin/companies/c1/members/u1/keys': () => [],
    'POST /v1/admin/companies/c1/members/u1/keys': () => { n += 1; return { status: 201, body: { id: `k${n}`, prefix: 'odin_ab', key: `odin_ab.SECRETO${n}` } }; },
  });
  await act('Ana', 'Claves de Ana');
  const give = async (label: string) => {
    await userEvent.type(await screen.findByLabelText('Etiqueta de la clave'), label);
    await userEvent.click(screen.getByRole('button', { name: 'Dar clave' }));
  };
  await give('uno');
  const first = await screen.findByRole('dialog', { name: 'Clave nueva' });
  first.removeAttribute('open');
  fireEvent(first, new Event('close'));
  await give('dos');
  await waitFor(() => expect(screen.getByRole('dialog', { name: 'Clave nueva' })).toHaveTextContent('SECRETO2'));
  expect(screen.getByRole('dialog', { name: 'Clave nueva' })).toHaveAttribute('open');
});

test('keysOnlyForWorkers', async () => {
  show({ [list]: () => [ana, bea] });
  await userEvent.click(await screen.findByRole('button', { name: 'Más acciones de Ana' }));
  await userEvent.click(screen.getByRole('button', { name: 'Más acciones de Bea' }));
  expect(screen.getByRole('button', { name: 'Claves de Ana' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Claves de Bea' })).toBeNull();
  expect(screen.getByRole('button', { name: 'Accesos de Ana' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Accesos de Bea' })).toBeNull();
});

test('accessesListAddAndRemoveProjects', async () => {
  const calls = show({
    [list]: () => [ana],
    'GET /v1/admin/companies/c1/members/u1/projects': () => [obra],
    [projectList]: () => [obra, reforma, sala, patio],
    'PUT /v1/admin/companies/c1/projects/p2/members/u1': () => ({ status: 204 }),
    'DELETE /v1/admin/companies/c1/projects/p1/members/u1': () => ({ status: 204 }),
  });
  await act('Ana', 'Accesos de Ana');
  const panel = await screen.findByRole('region', { name: 'Accesos de Ana' });
  expect(await within(panel).findByText('Obra')).toBeInTheDocument();
  expect(within(panel).getByText('Además ve los 2 proyectos visibles para toda la empresa.')).toBeInTheDocument();
  await userEvent.selectOptions(within(panel).getByLabelText('Proyecto'), 'Reforma');
  await userEvent.click(within(panel).getByRole('button', { name: 'Añadir' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'PUT /v1/admin/companies/c1/projects/p2/members/u1')).toBe(true));
  await userEvent.click(within(panel).getByRole('button', { name: 'Quitar Obra' }));
  await waitFor(() => expect(calls.some((c) => c.key === 'DELETE /v1/admin/companies/c1/projects/p1/members/u1')).toBe(true));
});

test('accessesAnnounceChangesKeepFocusAndRefreshTheProjects', async () => {
  const onProjectsChanged = vi.fn();
  show({
    [list]: () => [ana],
    'GET /v1/admin/companies/c1/members/u1/projects': () => [obra],
    [projectList]: () => [obra, reforma],
    'PUT /v1/admin/companies/c1/projects/p2/members/u1': () => ({ status: 204 }),
    'DELETE /v1/admin/companies/c1/projects/p1/members/u1': () => ({ status: 204 }),
  }, groupAdmin, onProjectsChanged);
  await act('Ana', 'Accesos de Ana');
  const panel = await screen.findByRole('region', { name: 'Accesos de Ana' });
  await userEvent.click(await within(panel).findByRole('button', { name: 'Quitar Obra' }));
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Quitado Obra'));
  expect(within(panel).getByRole('list', { name: 'Proyectos asignados' })).toHaveFocus();
  await userEvent.selectOptions(within(panel).getByLabelText('Proyecto'), 'Reforma');
  await userEvent.click(within(panel).getByRole('button', { name: 'Añadir' }));
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Añadido Reforma'));
  expect(onProjectsChanged).toHaveBeenCalledTimes(2);
});

test('aDeactivatedPersonCannotBeGivenProjects', async () => {
  show({
    [list]: () => [{ ...ana, active: false }],
    'GET /v1/admin/companies/c1/members/u1/projects': () => [],
    [projectList]: () => [obra],
  });
  await act('Ana', 'Accesos de Ana');
  const panel = await screen.findByRole('region', { name: 'Accesos de Ana' });
  expect(within(panel).getByText('Está desactivada: no se le pueden añadir proyectos.')).toBeInTheDocument();
  expect(within(panel).queryByRole('button', { name: 'Añadir' })).toBeNull();
});

test('personEmailsBreakOnlyAtAtOrDots', async () => {
  show({ [list]: () => [ana] });
  const row = await rowOf('Ana');
  expect(row.querySelectorAll('wbr').length).toBeGreaterThan(0);
  expect(row).toHaveTextContent('ana@acme.test');
});

test('resetPasswordOnlyForGroupAdminOnCompanyAdmins', async () => {
  const calls = show({ [list]: () => [ana, bea], 'PUT /v1/admin/users/u2/password': () => ({ status: 204 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Más acciones de Ana' }));
  await userEvent.click(screen.getByRole('button', { name: 'Más acciones de Bea' }));
  expect(screen.queryByRole('button', { name: 'Restablecer contraseña de Ana' })).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Restablecer contraseña de Bea' }));
  await userEvent.type(screen.getByLabelText('Nueva contraseña'), 'otra-clave-larga-2');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña de Bea' }));
  await waitFor(() =>
    expect(calls.find((c) => c.key === 'PUT /v1/admin/users/u2/password')?.body).toEqual({ newPassword: 'otra-clave-larga-2' }));
});

test('resetPasswordShowsConfirmation', async () => {
  show({ [list]: () => [ana, bea], 'PUT /v1/admin/users/u2/password': () => ({ status: 204 }) });
  await act('Bea', 'Restablecer contraseña de Bea');
  await userEvent.type(screen.getByLabelText('Nueva contraseña'), 'otra-clave-larga-2');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña de Bea' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Contraseña restablecida');
  expect(screen.queryByLabelText('Nueva contraseña')).toBeNull();
});

test('companyAdminDoesNotSeeResetPassword', async () => {
  show({ [list]: () => [bea] }, companyAdmin);
  await userEvent.click(await screen.findByRole('button', { name: 'Más acciones de Bea' }));
  expect(screen.queryByRole('button', { name: /Restablecer contraseña/ })).toBeNull();
});

test('addingAPersonRefreshesTheProjectsTheyJoined', async () => {
  const onProjectsChanged = vi.fn();
  mockFetch({
    [list]: () => [ana],
    [projectList]: () => [obra],
    'POST /v1/admin/companies/c1/members': () => ({ status: 201, body: { member: gil, alreadyExisted: false } }),
  });
  renderWithProviders(<Members companyId="c1" me={groupAdmin} addOpen onProjectsChanged={onProjectsChanged} />);
  await userEvent.type(await screen.findByLabelText('Nombre'), 'Cora');
  await userEvent.type(screen.getByLabelText('Apellidos'), 'Gil');
  await userEvent.click(await screen.findByLabelText('Obra'));
  await userEvent.click(screen.getByRole('button', { name: 'Añadir persona' }));
  await waitFor(() => expect(onProjectsChanged).toHaveBeenCalledTimes(1));
});
