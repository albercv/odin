import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../App';
import { mockFetch } from '../mockApi';

const me = { userId: 'u1', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1' };
const put = 'PUT /v1/admin/users/u1/password';

function routes(extra: Record<string, () => unknown>) {
  let loggedIn = true;
  const calls = mockFetch({
    'GET /v1/admin/me': () => (loggedIn ? me : { status: 401, body: { detail: 'Sesión caducada' } }),
    'GET /v1/admin/companies': () => [],
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c1/users': () => [],
    ...Object.fromEntries(Object.entries(extra).map(([k, h]) => [k, () => { const r = h(); if (k === put && !(r as { status: number }).status) loggedIn = false; return r; }])),
  });
  return calls;
}

async function openAccount() {
  render(<App />);
  expect(await screen.findByRole('link', { name: /Bea/ })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('link', { name: /Bea/ }));
}

test('changesOwnPasswordAndReturnsToLoginWithNotice', async () => {
  const calls = routes({ [put]: () => ({ status: 204 }) });
  await openAccount();
  await userEvent.type(screen.getByLabelText('Contraseña actual'), 'la-clave-vieja-1');
  await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'la-clave-nueva-22');
  await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Contraseña cambiada. Entra de nuevo.');
  expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument();
  expect(calls.find((c) => c.key === put)?.body).toEqual({
    currentPassword: 'la-clave-vieja-1', newPassword: 'la-clave-nueva-22',
  });
});

test('wrongCurrentPasswordShowsTheApiMessage', async () => {
  routes({ [put]: () => ({ status: 400, body: { detail: 'La contraseña actual no es correcta' } }) });
  await openAccount();
  await userEvent.type(screen.getByLabelText('Contraseña actual'), 'mala');
  await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'la-clave-nueva-22');
  await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña actual no es correcta');
  expect(screen.getByLabelText('Contraseña nueva')).toHaveValue('la-clave-nueva-22');
});
