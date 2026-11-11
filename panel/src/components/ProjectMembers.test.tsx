import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { Projects } from './Projects';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const base = '/v1/admin/companies/c1';
const project = (id: string, name: string, restricted: boolean) => ({ id, companyId: 'c1', name, restricted });
const person = (userId: string, displayName: string, roles: string[], active = true) => ({ userId, displayName, firstName: displayName, personCode: 'XXXX', memberCode: 'XXXX-ACM', roles, active });
const raul = { userId: 'u3', displayName: 'Raúl' };
const ana = { userId: 'u1', displayName: 'Ana' };

const show = (routes: Routes) => {
  const calls = mockFetch(routes);
  renderWithProviders(<Projects companyId="c1" />);
  return calls;
};
const openAccess = async (name: string) => {
  await userEvent.click(await screen.findByRole('button', { name: `Más acciones de ${name}` }));
  await userEvent.click(screen.getByRole('button', { name: `Accesos de ${name}` }));
  return screen.findByRole('region', { name: `Accesos de ${name}` });
};

test('whoSeesItShowsCompanyWideOrMemberCount', async () => {
  show({
    [`GET ${base}/projects`]: () => [project('p1', 'Obra', true), project('p2', 'Sala', false), project('p3', 'Taller', true), project('p4', 'Patio', true)],
    [`GET ${base}/projects/p1/members`]: () => [raul, ana],
    [`GET ${base}/projects/p2/members`]: () => [],
    [`GET ${base}/projects/p3/members`]: () => [raul],
    [`GET ${base}/projects/p4/members`]: () => [],
  });
  const row = async (name: string) => (await screen.findByText(name)).closest('tr')!;
  await waitFor(async () => expect(await row('Obra')).toHaveTextContent('2 miembros'));
  expect(await row('Sala')).toHaveTextContent('Toda la empresa');
  expect(await row('Taller')).toHaveTextContent('1 miembro');
  expect(await row('Taller')).not.toHaveTextContent('1 miembros');
  expect(await row('Patio')).toHaveTextContent('Nadie todavía');
});

test('closingAsksForConfirmationWhenNoMembers', async () => {
  const patch = `PATCH ${base}/projects/p2`;
  const calls = show({
    [`GET ${base}/projects`]: () => [project('p2', 'Sala', false)],
    [`GET ${base}/projects/p2/members`]: () => [],
    [`GET ${base}/members`]: () => [],
    [patch]: () => project('p2', 'Sala', true),
  });
  const panel = await openAccess('Sala');
  const box = within(panel).getByLabelText('Visible para toda la empresa');
  expect(box).toBeChecked();
  await userEvent.click(box);
  const dialog = screen.getByRole('dialog', { name: '¿Cerrar Sala? Solo lo verán sus miembros, y ahora no tiene ninguno.' });
  await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
  expect(calls.some((c) => c.key === patch)).toBe(false);
  expect(box).toBeChecked();
  await userEvent.click(box);
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cerrar' }));
  await waitFor(() => expect(calls.find((c) => c.key === patch)?.body).toEqual({ restricted: true }));
});

test('closingWithMembersDoesNotAskAndOpeningSendsFalse', async () => {
  const patch = `PATCH ${base}/projects/p2`;
  const calls = show({
    [`GET ${base}/projects`]: () => [project('p2', 'Sala', false), project('p1', 'Obra', true)],
    [`GET ${base}/projects/p2/members`]: () => [raul],
    [`GET ${base}/projects/p1/members`]: () => [],
    [`GET ${base}/members`]: () => [],
    [patch]: () => project('p2', 'Sala', true),
    [`PATCH ${base}/projects/p1`]: () => project('p1', 'Obra', false),
  });
  const sala = await openAccess('Sala');
  await userEvent.click(within(sala).getByLabelText('Visible para toda la empresa'));
  await waitFor(() => expect(calls.find((c) => c.key === patch)?.body).toEqual({ restricted: true }));
  expect(screen.queryByRole('dialog')).toBeNull();
  const obra = await openAccess('Obra');
  const box = within(obra).getByLabelText('Visible para toda la empresa');
  expect(box).not.toBeChecked();
  await userEvent.click(box);
  await waitFor(() => expect(calls.find((c) => c.key === `PATCH ${base}/projects/p1`)?.body).toEqual({ restricted: false }));
});

test('addsAndRemovesMembers', async () => {
  const calls = show({
    [`GET ${base}/projects`]: () => [project('p1', 'Obra', true)],
    [`GET ${base}/projects/p1/members`]: () => [raul],
    [`GET ${base}/members`]: () => [person('u1', 'Ana', ['WORKER']), person('u3', 'Raúl', ['WORKER']), person('u2', 'Bea', ['COMPANY_ADMIN']), person('u4', 'Eva', ['WORKER'], false)],
    [`PUT ${base}/projects/p1/members/u1`]: () => ({ status: 204 }),
    [`DELETE ${base}/projects/p1/members/u3`]: () => ({ status: 204 }),
  });
  const panel = await openAccess('Obra');
  expect(within(panel).getByLabelText('Visible para toda la empresa')).not.toBeChecked();
  expect(await within(panel).findByText('Raúl')).toBeInTheDocument();
  const options = within(within(panel).getByLabelText('Persona')).getAllByRole('option').map((o) => o.textContent);
  expect(options).toEqual(['Elige una persona', 'Ana']);
  await userEvent.selectOptions(within(panel).getByLabelText('Persona'), 'Ana');
  await userEvent.click(within(panel).getByRole('button', { name: 'Añadir' }));
  await waitFor(() => expect(calls.some((c) => c.key === `PUT ${base}/projects/p1/members/u1`)).toBe(true));
  await userEvent.click(within(panel).getByRole('button', { name: 'Quitar Raúl' }));
  await waitFor(() => expect(calls.some((c) => c.key === `DELETE ${base}/projects/p1/members/u3`)).toBe(true));
});
