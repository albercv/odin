import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../App';
import { mockFetch } from '../mockApi';
import { afterEach, beforeEach, describe, vi } from 'vitest';

vi.mock('../brain/BrainCanvas', () => ({ BrainCanvas: () => null }));

const me = { userId: 'u1', displayName: 'Bea Pérez', firstName: 'Bea', lastName: 'Pérez', role: 'COMPANY_ADMIN', companyId: 'c1', companies: [{ id: 'c1', name: 'Acme' }] };
const put = 'PUT /v1/admin/users/u1/password';

function routes(passwordReply: { status: number; body?: unknown }) {
  let loggedIn = true;
  return mockFetch({
    'GET /v1/admin/me': () => (loggedIn ? me : { status: 401, body: { detail: 'Sesión caducada' } }),
    'POST /v1/admin/session': () => { loggedIn = true; return { userId: 'u1' }; },
    'DELETE /v1/admin/session': () => { loggedIn = false; return { status: 204 }; },
    'GET /v1/admin/companies': () => [],
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c1/members': () => [],
    'PATCH /v1/admin/users/u1': () => ({ userId: 'u1' }),
    [put]: () => {
      if (passwordReply.status === 204) loggedIn = false;
      return passwordReply;
    },
  });
}

async function openAccount() {
  render(<App />);
  expect(await screen.findByRole('link', { name: 'Mi cuenta' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('link', { name: 'Mi cuenta' }));
}

async function changePassword(current: string) {
  await userEvent.type(screen.getByLabelText('Contraseña actual'), current);
  await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'la-clave-nueva-22');
  await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
}

test('changesOwnPasswordAndReturnsToLoginWithNotice', async () => {
  const calls = routes({ status: 204 });
  await openAccount();
  await changePassword('la-clave-vieja-1');
  expect(await screen.findByText('Contraseña cambiada. Entra de nuevo.')).toHaveAttribute('role', 'status');
  expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument();
  expect(calls.find((c) => c.key === put)?.body).toEqual({
    currentPassword: 'la-clave-vieja-1', newPassword: 'la-clave-nueva-22',
  });
});

test('editsOwnNamesAndRefreshesTheHeader', async () => {
  const calls = routes({ status: 204 });
  await openAccount();
  expect(screen.getByLabelText('Nombre')).toHaveValue('Bea');
  expect(screen.getByLabelText('Apellidos')).toHaveValue('Pérez');
  await userEvent.clear(screen.getByLabelText('Apellidos'));
  await userEvent.type(screen.getByLabelText('Apellidos'), 'Gil Ruiz');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar mis datos' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Datos guardados');
  expect(calls.find((c) => c.key === 'PATCH /v1/admin/users/u1')?.body).toEqual({ firstName: 'Bea', lastName: 'Gil Ruiz' });
  expect(calls.filter((c) => c.key === 'GET /v1/admin/me').length).toBeGreaterThan(1);
});

test('wrongCurrentPasswordShowsTheApiMessage', async () => {
  routes({ status: 400, body: { detail: 'La contraseña actual no es correcta' } });
  await openAccount();
  await changePassword('mala');
  expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña actual no es correcta');
  expect(screen.getByLabelText('Contraseña nueva')).toHaveValue('la-clave-nueva-22');
});

test('noticeDisappearsAfterLoginAndDoesNotReturnOnLogout', async () => {
  routes({ status: 204 });
  await openAccount();
  await changePassword('la-clave-vieja-1');
  expect(await screen.findByText('Contraseña cambiada. Entra de nuevo.')).toBeInTheDocument();
  await userEvent.type(screen.getByLabelText('Correo'), 'bea@acme.test');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'la-clave-nueva-22');
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Salir' }));
  expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument();
  expect(screen.queryByText('Contraseña cambiada. Entra de nuevo.')).toBeNull();
});

afterEach(() => localStorage.clear());

test('themeChoiceIsSaved', async () => {
  routes({ status: 204 });
  await openAccount();
  expect(screen.getByRole('radio', { name: 'Como el sistema' })).toBeChecked();
  await userEvent.click(screen.getByRole('radio', { name: 'Oscuro' }));
  expect(screen.getByRole('radio', { name: 'Oscuro' })).toBeChecked();
  expect(localStorage.getItem('odin.colorPreference')).toBe('dark');
});

describe('colorModeAtTheRoot', () => {
  let systemDark = false;
  let listeners: (() => void)[] = [];
  beforeEach(() => {
    systemDark = false; listeners = [];
    vi.stubGlobal('matchMedia', () => ({
      get matches() { return systemDark; },
      addEventListener: (_: string, l: () => void) => listeners.push(l),
      removeEventListener: (_: string, l: () => void) => { listeners = listeners.filter((x) => x !== l); },
    }));
  });
  afterEach(() => vi.unstubAllGlobals());

  test('rootFollowsTheSystemOnTheLoginScreen', async () => {
    mockFetch({ 'GET /v1/admin/me': () => ({ status: 401, body: { detail: 'x' } }) });
    render(<App />);
    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument();
    systemDark = true;
    act(() => listeners.forEach((l) => l()));
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  test('accountChoiceAppliesAtOnceWithASingleListener', async () => {
    routes({ status: 204 });
    await openAccount();
    expect(listeners).toHaveLength(1);
    await userEvent.click(screen.getByRole('radio', { name: 'Oscuro' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
    await userEvent.click(screen.getByRole('radio', { name: 'Como el sistema' }));
    expect(listeners).toHaveLength(1);
  });
});
