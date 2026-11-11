import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Company } from './Company';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

type Me = Parameters<typeof Company>[0]['me'];
const groupAdmin: Me = { userId: 'g', displayName: 'Ada', role: 'GROUP_ADMIN', companies: [] };
const companyAdmin: Me = { userId: 'u2', displayName: 'Bea', role: 'COMPANY_ADMIN', companyId: 'c1', companies: [{ id: 'c1', name: 'Acme' }] };

function show(me: Me) {
  mockFetch({
    'GET /v1/admin/companies': () => [{ id: 'c1', name: 'Acme', capabilities: ['SEARCH_KNOWLEDGE'] }],
    'GET /v1/admin/companies/c1/projects': () => [],
    'GET /v1/admin/companies/c1/members': () => [],
  });
  renderWithProviders(<Company companyId="c1" me={me} />);
}

test('capabilitiesReadOnlyForCompanyAdmin', async () => {
  show(companyAdmin);
  expect(await screen.findByRole('heading', { name: 'Acme' })).toBeInTheDocument();
  expect(await screen.findByText('Buscar conocimiento · activa')).toBeInTheDocument();
  expect(screen.getByText('Guardar fichas · desactivada')).toBeInTheDocument();
  expect(screen.queryByRole('switch')).toBeNull();
});

test('groupAdminGetsSwitches', async () => {
  show(groupAdmin);
  expect(await screen.findByRole('switch', { name: 'Buscar conocimiento' })).toBeChecked();
  expect(screen.getByRole('switch', { name: 'Guardar fichas' })).not.toBeChecked();
});

test('addPersonOpensADialog', async () => {
  show(companyAdmin);
  await userEvent.click(await screen.findByRole('button', { name: 'Añadir persona' }));
  expect(screen.getByRole('dialog', { name: 'Añadir persona' })).toBeInTheDocument();
  expect(screen.getByLabelText('Nombre')).toHaveFocus();
});
