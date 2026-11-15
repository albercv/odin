import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Company } from './Company';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

type Me = Parameters<typeof Company>[0]['me'];
const groupAdmin: Me = { userId: 'g', displayName: 'Ada', firstName: 'Ada', personCode: 'GGGG', role: 'GROUP_ADMIN', companies: [], scannedDocumentsSupported: false };
const companyAdmin: Me = { userId: 'u2', displayName: 'Bea', firstName: 'Bea', personCode: 'BBBB', role: 'COMPANY_ADMIN', companyId: 'c1', companies: [{ id: 'c1', name: 'Acme', code: 'ACM', codeReviewed: true, projectLabel: { singular: 'proyecto', plural: 'proyectos', feminine: false } }], scannedDocumentsSupported: false };

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
  expect(screen.getByText('Casos parecidos · desactivada')).toBeInTheDocument();
  expect(screen.getByText('Subir documentos · desactivada')).toBeInTheDocument();
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

test('removingAProjectsOnlyMemberFromAccessesRefreshesTheProjectConfirmation', async () => {
  let members = [{ userId: 'u1', displayName: 'Ana' }];
  const project = () => ({ id: 'p1', companyId: 'c1', name: 'Sala', restricted: false, memberCount: members.length });
  mockFetch({
    'GET /v1/admin/companies': () => [{ id: 'c1', name: 'Acme', code: 'ACM', capabilities: [] }],
    'GET /v1/admin/companies/c1/projects': () => [project()],
    'GET /v1/admin/companies/c1/projects/p1/members': () => members,
    'GET /v1/admin/companies/c1/members': () => [{ userId: 'u1', displayName: 'Ana', firstName: 'Ana', personCode: 'AAAA', memberCode: 'AAAA-ACM', roles: ['WORKER'], active: true, hasPassword: false }],
    'GET /v1/admin/companies/c1/members/u1/projects': () => (members.length ? [project()] : []),
    'DELETE /v1/admin/companies/c1/projects/p1/members/u1': () => { members = []; return { status: 204 }; },
  });
  renderWithProviders(<Company companyId="c1" me={companyAdmin} />);
  await userEvent.click(await screen.findByRole('button', { name: 'Más acciones de Ana' }));
  await userEvent.click(screen.getByRole('button', { name: 'Accesos de Ana' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Quitar Sala' }));
  await waitFor(() => expect(screen.getByRole('row', { name: /Sala/ })).toHaveTextContent('Toda la empresa'));
  await userEvent.click(screen.getByRole('button', { name: 'Más acciones de Sala' }));
  await userEvent.click(screen.getByRole('button', { name: 'Miembros de Sala' }));
  await userEvent.click(await screen.findByLabelText('Visible para toda la empresa'));
  expect(await screen.findByRole('dialog', { name: /¿Cerrar Sala\?/ })).toBeInTheDocument();
});
