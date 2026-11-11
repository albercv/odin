import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { Users } from './Users';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

type Me = Parameters<typeof Users>[0]['me'];

afterEach(() => vi.restoreAllMocks());

const ana = { userId: 'u1', displayName: 'Ana', firstName: 'Ana', personCode: 'AAAA', memberCode: 'AAAA-ACM', email: 'ana@acme.test', roles: ['WORKER'], active: true };
const bea = { userId: 'u2', displayName: 'Bea', firstName: 'Bea', personCode: 'BBBB', memberCode: 'BBBB-ACM', roles: ['COMPANY_ADMIN'], active: false, email: 'bea@acme.test' };
const worker = { userId: 'u3', displayName: 'Raúl', firstName: 'Raúl', personCode: 'RRRR', memberCode: 'RRRR-ACM', roles: ['WORKER'], active: true };
const list = 'GET /v1/admin/companies/c1/members';
const groupAdmin: Me = { userId: 'g', displayName: 'Ada', personCode: 'GGGG', role: 'GROUP_ADMIN', companies: [] };
const companyAdmin: Me = { userId: 'u2', displayName: 'Bea', personCode: 'BBBB', role: 'COMPANY_ADMIN', companyId: 'c1', companies: [{ id: 'c1', name: 'Acme', code: 'ACM' }] };

const show = (routes: Routes, me: Me = groupAdmin) => {
  const calls = mockFetch(routes);
  renderWithProviders(<Users companyId="c1" me={me} />);
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

test('rowShowsEmailRoleAndState', async () => {
  show({ [list]: () => [ana, bea, worker] });
  const [anaRow, beaRow, raulRow] = [await rowOf('Ana'), await rowOf('Bea'), await rowOf('Raúl')];
  ['ana@acme.test', 'Trabajador', 'Activo'].forEach((t) => expect(anaRow).toHaveTextContent(t));
  ['bea@acme.test', 'Administrador', 'Desactivado'].forEach((t) => expect(beaRow).toHaveTextContent(t));
  ['Sin correo', 'Trabajador', 'Activo'].forEach((t) => expect(raulRow).toHaveTextContent(t));
});

test('activateSendsActiveTrue', async () => {
  const calls = show({ [list]: () => [bea], 'PATCH /v1/admin/companies/c1/members/u2': () => ({ ...bea, active: true }) });
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

test('removeAsksWithTheConfirmDialog', async () => {
  let users = [ana];
  const calls = show({
    [list]: () => users,
    'DELETE /v1/admin/companies/c1/members/u1': () => { users = []; return { status: 204 }; },
  });
  await act('Ana', 'Quitar Ana');
  const dialog = screen.getByRole('dialog', { name: '¿Quitar a Ana de la empresa?' });
  await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  expect(screen.queryByRole('dialog')).toBeNull();
  await act('Ana', 'Quitar Ana');
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Quitar' }));
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Más acciones de Ana' })).toBeNull());
  expect(calls.some((c) => c.key === 'DELETE /v1/admin/companies/c1/members/u1')).toBe(true);
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
