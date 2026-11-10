import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { Projects } from './Projects';
import { mockFetch, type Routes } from '../mockApi';

const obra = { id: 'p1', name: 'Obra' };
const list = 'GET /v1/admin/companies/c1/projects';
const one = '/v1/admin/companies/c1/projects/p1';

const show = (routes: Routes) => {
  const calls = mockFetch(routes);
  render(<Projects companyId="c1" />);
  return calls;
};

test('renamesProject', async () => {
  const calls = show({ [list]: () => [obra], [`PATCH ${one}`]: () => ({ ...obra, name: 'Reforma' }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Renombrar Obra' }));
  await userEvent.clear(screen.getByLabelText('Nombre'));
  await userEvent.type(screen.getByLabelText('Nombre'), 'Reforma');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Obra' }));
  await waitFor(() => expect(calls.find((c) => c.key === `PATCH ${one}`)?.body).toEqual({ name: 'Reforma' }));
});

test('deleteAsksForConfirmation', async () => {
  const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
  const calls = show({ [list]: () => [obra], [`DELETE ${one}`]: () => ({ status: 204 }) });
  await userEvent.click(await screen.findByRole('button', { name: 'Borrar Obra' }));
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  await userEvent.click(screen.getByRole('button', { name: 'Borrar Obra' }));
  expect(confirm).toHaveBeenCalledWith('¿Borrar el proyecto Obra? Sus documentos se conservan.');
  await waitFor(() => expect(calls.some((c) => c.key === `DELETE ${one}`)).toBe(true));
});

test('duplicateNameShowsTheApiMessage', async () => {
  show({
    [list]: () => [obra],
    [`PATCH ${one}`]: () => ({ status: 409, body: { detail: 'Ya existe un proyecto con ese nombre' } }),
  });
  await userEvent.click(await screen.findByRole('button', { name: 'Renombrar Obra' }));
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Obra' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Ya existe un proyecto con ese nombre');
  expect(screen.getByLabelText('Nombre')).toHaveValue('Obra');
});

test('failedAddKeepsTheTypedName', async () => {
  show({
    [list]: () => [],
    'POST /v1/admin/companies/c1/projects': () => ({ status: 409, body: { detail: 'Ya existe un proyecto con ese nombre' } }),
  });
  await userEvent.type(await screen.findByLabelText('Nombre del proyecto'), 'Obra');
  await userEvent.click(screen.getByRole('button', { name: 'Añadir' }));
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  expect(screen.getByLabelText('Nombre del proyecto')).toHaveValue('Obra');
});
