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
