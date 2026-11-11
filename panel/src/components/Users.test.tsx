import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { Users } from './Users';
import { mockFetch, type Routes } from '../mockApi';

afterEach(() => vi.restoreAllMocks());

const ana = { id: 'u1', displayName: 'Ana', email: 'ana@acme.test', role: 'WORKER', companyId: 'c1', active: true };
const bea = { id: 'u2', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1', active: false, email: 'bea@acme.test' };
const worker = { id: 'u3', displayName: 'Raúl', role: 'WORKER', companyId: 'c1', active: true };
const list = 'GET /v1/admin/companies/c1/users';
const groupAdmin = { userId: 'g', displayName: 'Ada', role: 'GROUP_ADMIN' } as const;
const companyAdmin = { userId: 'u2', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1' } as const;

const show = (routes: Routes, me: Parameters<typeof Users>[0]['me'] = groupAdmin) => {
  const calls = mockFetch(routes);
  render(<Users companyId="c1" me={me} />);
  return calls;
};
const rowOf = async (name: string) =>
  waitFor(() => {
    const row = screen.getAllByRole('listitem').find((li) => li.textContent?.startsWith(name));
    if (!row) throw new Error(`sin fila ${name}`);
    return row;
  });

test('rowShowsEmailRoleAndState', async () => {
  show({ [list]: () => [ana, bea, worker] });
  expect((await rowOf('Ana')).textContent).toContain('Ana · ana@acme.test · Trabajador · activo');
  expect((await rowOf('Bea')).textContent).toContain('Bea · bea@acme.test · Administrador · desactivado');
  expect((await rowOf('Raúl')).textContent).toContain('Raúl · sin correo · Trabajador · activo');
});

test('activateSendsActiveTrue', async () => {
  const calls = show({ [list]: () => [bea], 'PATCH /v1/admin/users/u2': () => ({ ...bea, active: true }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Activar Bea' }));
  await waitFor(() => expect(calls.find((c) => c.key === 'PATCH /v1/admin/users/u2')?.body).toEqual({ active: true }));
});

test('editSavesNameAndEmail', async () => {
  const calls = show({ [list]: () => [ana], 'PATCH /v1/admin/users/u1': () => ana });
  await userEvent.click(await screen.findByRole('button', { name: 'Editar Ana' }));
  const row = screen.getByRole('button', { name: 'Guardar Ana' }).closest('li')!;
  await userEvent.clear(within(row).getByLabelText('Nombre'));
  await userEvent.type(within(row).getByLabelText('Nombre'), 'Anita');
  await userEvent.clear(within(row).getByLabelText('Correo'));
  await userEvent.click(within(row).getByRole('button', { name: 'Guardar Ana' }));
  await waitFor(() =>
    expect(calls.find((c) => c.key === 'PATCH /v1/admin/users/u1')?.body).toEqual({ displayName: 'Anita', email: '' }));
});

test('conflictShowsTheApiMessage', async () => {
  show({
    [list]: () => [ana],
    'PATCH /v1/admin/users/u1': () => ({ status: 409, body: { detail: 'Ese correo ya existe' } }),
  });
  await userEvent.click(await screen.findByRole('button', { name: 'Editar Ana' }));
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Ana' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Ese correo ya existe');
  expect(within(screen.getByRole('button', { name: 'Guardar Ana' }).closest('li')!).getByLabelText('Nombre')).toHaveValue('Ana');
});

test('deleteAsksForConfirmationAndRemovesTheRow', async () => {
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
  let users = [ana];
  const calls = show({
    [list]: () => users,
    'DELETE /v1/admin/users/u1': () => { users = []; return { status: 204 }; },
  });
  await userEvent.click(await screen.findByRole('button', { name: 'Borrar Ana' }));
  expect(confirm).toHaveBeenCalledWith('¿Borrar a Ana? No se puede deshacer.');
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Borrar Ana' })).toBeNull());
  expect(calls.some((c) => c.key === 'DELETE /v1/admin/users/u1')).toBe(true);
});

test('cancelledConfirmationDeletesNothing', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(false);
  const calls = show({ [list]: () => [ana] });
  await userEvent.click(await screen.findByRole('button', { name: 'Borrar Ana' }));
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  expect(screen.getByRole('button', { name: 'Borrar Ana' })).toBeInTheDocument();
});

test('keysOnlyForWorkers', async () => {
  show({ [list]: () => [ana, bea] });
  expect(await screen.findByRole('button', { name: 'Claves de Ana' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Claves de Bea' })).toBeNull();
});

test('resetPasswordOnlyForGroupAdminOnCompanyAdmins', async () => {
  const calls = show({ [list]: () => [ana, bea], 'PUT /v1/admin/users/u2/password': () => ({ status: 204 }) });
  expect(await screen.findByRole('button', { name: 'Restablecer contraseña de Bea' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Restablecer contraseña de Ana' })).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Restablecer contraseña de Bea' }));
  await userEvent.type(screen.getByLabelText('Nueva contraseña'), 'otra-clave-larga-2');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña de Bea' }));
  await waitFor(() =>
    expect(calls.find((c) => c.key === 'PUT /v1/admin/users/u2/password')?.body).toEqual({ newPassword: 'otra-clave-larga-2' }));
});

test('resetPasswordShowsConfirmation', async () => {
  show({ [list]: () => [ana, bea], 'PUT /v1/admin/users/u2/password': () => ({ status: 204 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Restablecer contraseña de Bea' }));
  await userEvent.type(screen.getByLabelText('Nueva contraseña'), 'otra-clave-larga-2');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña de Bea' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Contraseña restablecida');
  expect(screen.queryByLabelText('Nueva contraseña')).toBeNull();
});

test('companyAdminDoesNotSeeResetPassword', async () => {
  show({ [list]: () => [bea] }, companyAdmin);
  await rowOf('Bea');
  expect(screen.queryByRole('button', { name: /Restablecer contraseña/ })).toBeNull();
});
