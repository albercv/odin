import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Companies } from './Companies';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

const list = 'GET /v1/admin/companies';
const create = 'POST /v1/admin/companies';

test('newCompanyAsksForNameAndCodeAndSendsBoth', async () => {
  const calls = mockFetch({
    [list]: () => [],
    [create]: () => ({ status: 201, body: { id: 'c9', name: 'Acme', code: 'ACM', capabilities: [] } }),
  });
  renderWithProviders(<Companies onOpen={() => {}} />);
  await userEvent.click(await screen.findByRole('button', { name: 'Nueva empresa' }));
  await userEvent.type(screen.getByLabelText('Nombre de la empresa'), 'Acme');
  await userEvent.type(screen.getByLabelText('Código'), 'acm');
  await userEvent.click(screen.getByRole('button', { name: 'Crear empresa' }));
  await waitFor(() => expect(calls.find((c) => c.key === create)?.body).toEqual({ name: 'Acme', code: 'ACM' }));
});

const acme = { id: 'c1', name: 'Acme', code: 'ACM', capabilities: [] };
const edit = async () => {
  const calls = mockFetch({ [list]: () => [acme], 'PATCH /v1/admin/companies/c1': () => acme });
  renderWithProviders(<Companies onOpen={() => {}} />);
  await userEvent.click(await screen.findByRole('button', { name: 'Más acciones de Acme' }));
  await userEvent.click(screen.getByRole('button', { name: 'Editar Acme' }));
  return calls;
};

test('codeIsRequiredOnCreate', async () => {
  const calls = mockFetch({ [list]: () => [], [create]: () => ({ status: 201, body: acme }) });
  renderWithProviders(<Companies onOpen={() => {}} />);
  await userEvent.click(await screen.findByRole('button', { name: 'Nueva empresa' }));
  expect(screen.getByLabelText('Código')).toBeRequired();
  await userEvent.type(screen.getByLabelText('Nombre de la empresa'), 'Acme');
  await userEvent.click(screen.getByRole('button', { name: 'Crear empresa' }));
  expect(calls.some((c) => c.key === create)).toBe(false);
});

test('editsNameAndCode', async () => {
  const calls = await edit();
  expect(screen.getByLabelText('Código')).toHaveValue('ACM');
  await userEvent.clear(screen.getByLabelText('Código'));
  await userEvent.type(screen.getByLabelText('Código'), 'acx');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Acme' }));
  await waitFor(() => expect(calls.find((c) => c.key === 'PATCH /v1/admin/companies/c1')?.body).toEqual({ name: 'Acme', code: 'ACX' }));
});

test('rowButtonsShowShortTextAndNameTheTarget', async () => {
  await edit();
  expect(screen.getByRole('button', { name: 'Abrir Acme' })).toHaveTextContent(/^Abrir$/);
  expect(screen.getByRole('button', { name: 'Guardar Acme' })).toHaveTextContent(/^Guardar$/);
  expect(screen.getByRole('button', { name: 'Cancelar Acme' })).toHaveTextContent(/^Cancelar$/);
});
