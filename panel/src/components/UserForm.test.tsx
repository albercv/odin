import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserForm } from './UserForm';
import { mockFetch } from '../mockApi';

const created = { status: 201, body: { id: 'u9', displayName: 'Ana', role: 'WORKER', active: true } };
const path = 'POST /v1/admin/companies/c1/users';

test('workerFormHasNoPasswordAndEmailIsOptional', async () => {
  const calls = mockFetch({ [path]: () => created });
  const onDone = vi.fn();
  render(<UserForm companyId="c1" onDone={onDone} />);
  expect(screen.queryByLabelText('Contraseña')).toBeNull();
  expect(screen.getByLabelText('Correo')).not.toBeRequired();
  await userEvent.type(screen.getByLabelText('Nombre'), 'Ana');
  await userEvent.click(screen.getByRole('button', { name: 'Crear usuario' }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(calls.find((c) => c.key === path)?.body).toEqual({ displayName: 'Ana', role: 'WORKER' });
});

test('adminFormRequiresPassword', async () => {
  const calls = mockFetch({ [path]: () => created });
  render(<UserForm companyId="c1" onDone={() => {}} />);
  await userEvent.selectOptions(screen.getByLabelText('Rol'), 'COMPANY_ADMIN');
  expect(screen.getByLabelText('Contraseña')).toBeRequired();
  expect(screen.getByLabelText('Correo')).toBeRequired();
  await userEvent.type(screen.getByLabelText('Nombre'), 'Bea');
  await userEvent.type(screen.getByLabelText('Correo'), 'bea@acme.test');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'una-clave-larga-1');
  await userEvent.click(screen.getByRole('button', { name: 'Crear usuario' }));
  await waitFor(() => expect(calls.some((c) => c.key === path)).toBe(true));
  expect(calls.find((c) => c.key === path)?.body).toEqual({
    displayName: 'Bea', email: 'bea@acme.test', role: 'COMPANY_ADMIN', password: 'una-clave-larga-1',
  });
});

test('failedCreateKeepsTheTypedValues', async () => {
  mockFetch({ [path]: () => ({ status: 409, body: { detail: 'Ese correo ya existe' } }) });
  render(<UserForm companyId="c1" onDone={() => {}} />);
  await userEvent.type(screen.getByLabelText('Nombre'), 'Ana');
  await userEvent.type(screen.getByLabelText('Correo'), 'ana@acme.test');
  await userEvent.click(screen.getByRole('button', { name: 'Crear usuario' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Ese correo ya existe');
  expect(screen.getByLabelText('Nombre')).toHaveValue('Ana');
  expect(screen.getByLabelText('Correo')).toHaveValue('ana@acme.test');
});
