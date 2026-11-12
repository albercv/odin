import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GroupAdmins } from './GroupAdmins';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

type Me = Parameters<typeof GroupAdmins>[0]['me'];

const list = 'GET /v1/admin/group-admins';
const me: Me = { userId: 'g1', displayName: 'Ada Rey', firstName: 'Ada', lastName: 'Rey', personCode: 'ADAA', role: 'GROUP_ADMIN', companies: [] };
const ada = { userId: 'g1', firstName: 'Ada', lastName: 'Rey', displayName: 'Ada Rey', email: 'ada@demo.test', personCode: 'ADAA', active: true };
const gael = { userId: 'g2', firstName: 'Gael', lastName: 'Ruiz', displayName: 'Gael Ruiz', email: 'gael@demo.test', personCode: 'GAEL', active: true };
const gus = { ...gael, userId: 'g3', displayName: 'Gus Paz', firstName: 'Gus', lastName: 'Paz', email: 'gus@demo.test', personCode: 'GUSS', active: false };

const show = (routes: Routes) => {
  const calls = mockFetch({ [list]: () => [ada, gael, gus], ...routes });
  renderWithProviders(<GroupAdmins me={me} />);
  return calls;
};
const act = async (person: string, action: string) => {
  await userEvent.click(await screen.findByRole('button', { name: `Más acciones de ${person}` }));
  await userEvent.click(screen.getByRole('button', { name: `${action} ${person}` }));
};
const confirmWith = async (label: string) => userEvent.click(screen.getByRole('button', { name: label }));

test('tableShowsCodePersonAndState', async () => {
  show({});
  const row = await screen.findByRole('row', { name: /Gael Ruiz/ });
  ['GAEL', 'Gael Ruiz', 'gael@demo.test', 'Activo'].forEach((t) => expect(row).toHaveTextContent(t));
  expect(screen.getByRole('row', { name: /Gus Paz/ })).toHaveTextContent('Desactivado');
});

test('ownRowHasNoActions', async () => {
  show({});
  await screen.findByRole('row', { name: /Gael Ruiz/ });
  expect(screen.queryByRole('button', { name: 'Más acciones de Ada Rey' })).toBeNull();
  expect(screen.getByRole('button', { name: 'Más acciones de Gael Ruiz' })).toBeInTheDocument();
});

test('addsAGroupAdminAndSendsTheForm', async () => {
  const calls = show({ 'POST /v1/admin/group-admins': () => ({ status: 201, body: { admin: gael, alreadyExisted: false, keptPassword: false } }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Añadir administrador del grupo' }));
  await userEvent.type(screen.getByLabelText('Nombre'), 'Gael');
  await userEvent.type(screen.getByLabelText('Apellidos'), 'Ruiz');
  expect(screen.getByLabelText('Correo')).toBeRequired();
  await userEvent.type(screen.getByLabelText('Correo'), 'gael@demo.test');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'una-clave-larga-1');
  await userEvent.click(screen.getByRole('button', { name: 'Añadir administrador' }));
  await waitFor(() => expect(calls.find((c) => c.key === 'POST /v1/admin/group-admins')?.body)
    .toEqual({ firstName: 'Gael', lastName: 'Ruiz', email: 'gael@demo.test', password: 'una-clave-larga-1' }));
});

test('anExistingPersonIsAnnouncedAndKeepsThePassword', async () => {
  show({ 'POST /v1/admin/group-admins': () => ({ status: 201, body: { admin: gael, alreadyExisted: true, keptPassword: true } }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Añadir administrador del grupo' }));
  await userEvent.type(screen.getByLabelText('Nombre'), 'Gael');
  await userEvent.type(screen.getByLabelText('Apellidos'), 'Ruiz');
  await userEvent.type(screen.getByLabelText('Correo'), 'gael@demo.test');
  await userEvent.click(screen.getByRole('button', { name: 'Añadir administrador' }));
  expect(await screen.findByText(/ya existía en el grupo.*Conserva su contraseña/)).toBeInTheDocument();
});

test('deactivatesAfterConfirming', async () => {
  const calls = show({ 'PATCH /v1/admin/users/g2': () => ({ userId: 'g2' }) });
  await act('Gael Ruiz', 'Desactivar');
  expect(screen.getByText('¿Desactivar a Gael Ruiz en todo el grupo? No podrá usar Odín en ninguna empresa hasta que se reactive. Sus claves y sus proyectos se conservan.')).toBeInTheDocument();
  expect(calls.some((c) => c.key.startsWith('PATCH'))).toBe(false);
  await confirmWith('Desactivar');
  await waitFor(() => expect(calls.find((c) => c.key === 'PATCH /v1/admin/users/g2')?.body).toEqual({ active: false }));
});

test('activatesAfterConfirming', async () => {
  const calls = show({ 'PATCH /v1/admin/users/g3': () => ({ userId: 'g3' }) });
  await act('Gus Paz', 'Activar');
  await confirmWith('Activar');
  await waitFor(() => expect(calls.find((c) => c.key === 'PATCH /v1/admin/users/g3')?.body).toEqual({ active: true }));
});

test('revokesTheMarkAfterConfirming', async () => {
  const calls = show({ 'DELETE /v1/admin/group-admins/g2': () => ({ status: 204 }) });
  await act('Gael Ruiz', 'Quitar administración');
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  await confirmWith('Quitar administración');
  await waitFor(() => expect(calls.some((c) => c.key === 'DELETE /v1/admin/group-admins/g2')).toBe(true));
});

test('deletesAfterConfirming', async () => {
  const calls = show({ 'DELETE /v1/admin/users/g2': () => ({ status: 204 }) });
  await act('Gael Ruiz', 'Borrar');
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  await confirmWith('Borrar');
  await waitFor(() => expect(calls.some((c) => c.key === 'DELETE /v1/admin/users/g2')).toBe(true));
});

test('cancellingTheConfirmationChangesNothing', async () => {
  const calls = show({});
  await act('Gael Ruiz', 'Desactivar');
  await confirmWith('Cancelar');
  expect(calls.every((c) => c.key === list)).toBe(true);
});

test('theLastActiveRuleShowsTheServerMessage', async () => {
  show({ 'PATCH /v1/admin/users/g2': () => ({ status: 409, body: { detail: 'Tiene que quedar al menos un administrador del grupo activo' } }) });
  await act('Gael Ruiz', 'Desactivar');
  await confirmWith('Desactivar');
  expect(await screen.findByText('Tiene que quedar al menos un administrador del grupo activo')).toBeInTheDocument();
});
