import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemberForm } from './MemberForm';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

const path = 'POST /v1/admin/companies/c1/members';
const projects = [
  { id: 'p1', companyId: 'c1', name: 'Obra', restricted: true },
  { id: 'p2', companyId: 'c1', name: 'Reforma', restricted: true },
  { id: 'p3', companyId: 'c1', name: 'Sala común', restricted: false },
];
const created = (alreadyExisted = false, keptPassword = false) => ({
  status: 201,
  body: { member: { userId: 'u9', displayName: 'Ana Gil', roles: ['WORKER'], active: true }, alreadyExisted, keptPassword },
});

function show(routes: Routes, isGroupAdmin = true) {
  const calls = mockFetch({ 'GET /v1/admin/companies/c1/projects': () => projects, ...routes });
  const onDone = vi.fn();
  renderWithProviders(<MemberForm companyId="c1" isGroupAdmin={isGroupAdmin} onDone={onDone} />);
  return { calls, onDone };
}
const sent = (calls: { key: string; body: unknown }[]) => calls.find((c) => c.key === path)?.body;
const fillNames = async (first = 'Ana', last = 'Gil') => {
  await userEvent.type(screen.getByLabelText('Nombre'), first);
  await userEvent.type(screen.getByLabelText('Apellidos'), last);
};
const submit = () => userEvent.click(screen.getByRole('button', { name: 'Añadir persona' }));

test('workerOnlySendsNoPassword', async () => {
  const { calls, onDone } = show({ [path]: () => created() });
  expect(screen.queryByLabelText('Contraseña')).toBeNull();
  expect(screen.getByLabelText('Correo')).not.toBeRequired();
  expect(screen.getByLabelText('Trabaja con su IA')).toBeChecked();
  await fillNames();
  await submit();
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(sent(calls)).toEqual({ firstName: 'Ana', lastName: 'Gil', roles: ['WORKER'] });
});

test('adminRoleRequiresPassword', async () => {
  const { calls } = show({ [path]: () => created() });
  await userEvent.click(screen.getByLabelText('Administra la empresa'));
  await userEvent.click(screen.getByLabelText('Trabaja con su IA'));
  expect(screen.getByLabelText('Contraseña')).toBeRequired();
  expect(screen.getByLabelText('Correo')).toBeRequired();
  await fillNames('Bea', 'Ruiz');
  await userEvent.type(screen.getByLabelText('Correo'), 'bea@acme.test');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'una-clave-larga-1');
  await submit();
  await waitFor(() => expect(sent(calls)).toEqual({
    firstName: 'Bea', lastName: 'Ruiz', email: 'bea@acme.test', roles: ['COMPANY_ADMIN'], password: 'una-clave-larga-1',
  }));
});

test('adminCheckboxOnlyForGroupAdmin', () => {
  show({}, false);
  expect(screen.queryByLabelText('Administra la empresa')).toBeNull();
  expect(screen.getByLabelText('Trabaja con su IA')).toBeInTheDocument();
});

test('atLeastOneRoleIsRequired', async () => {
  const { calls } = show({ [path]: () => created() });
  await fillNames();
  await userEvent.click(screen.getByLabelText('Trabaja con su IA'));
  await submit();
  expect(await screen.findByRole('alert')).toHaveTextContent('Elige al menos un papel');
  expect(sent(calls)).toBeUndefined();
});

test('firstAndLastNameAreRequired', async () => {
  const { calls } = show({ [path]: () => created() });
  expect(screen.getByLabelText('Nombre')).toBeRequired();
  expect(screen.getByLabelText('Apellidos')).toBeRequired();
  await submit();
  expect(sent(calls)).toBeUndefined();
});

test('projectsOnlyForWorkersAndSentAsProjectIds', async () => {
  const { calls } = show({ [path]: () => created() });
  const obra = await screen.findByLabelText('Obra');
  expect(obra).not.toBeChecked();
  expect(screen.getByLabelText('Reforma')).not.toBeChecked();
  await userEvent.click(screen.getByLabelText('Reforma'));
  await fillNames();
  await submit();
  await waitFor(() => expect(sent(calls)).toEqual({ firstName: 'Ana', lastName: 'Gil', roles: ['WORKER'], projectIds: ['p2'] }));
});

test('projectsAreHiddenAndNotSentWithoutTheWorkerRole', async () => {
  const { calls } = show({ [path]: () => created() });
  await userEvent.click(await screen.findByLabelText('Obra'));
  await userEvent.click(screen.getByLabelText('Administra la empresa'));
  await userEvent.click(screen.getByLabelText('Trabaja con su IA'));
  expect(screen.queryByLabelText('Obra')).toBeNull();
  await fillNames('Bea', 'Ruiz');
  await userEvent.type(screen.getByLabelText('Correo'), 'bea@acme.test');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'una-clave-larga-1');
  await submit();
  await waitFor(() => expect(sent(calls)).toMatchObject({ roles: ['COMPANY_ADMIN'] }));
  expect(sent(calls)).not.toHaveProperty('projectIds');
});

test('onlyClosedProjectsAreOfferedAndTheWideOnesAreCounted', async () => {
  show({});
  await screen.findByLabelText('Obra');
  expect(screen.queryByLabelText('Sala común')).toBeNull();
  expect(screen.getByText('Además verá 1 proyecto visible para toda la empresa.')).toBeInTheDocument();
});

test('alreadyExistedShowsTheNotice', async () => {
  const { onDone } = show({ [path]: () => created(true) });
  await fillNames();
  await submit();
  expect(await screen.findByRole('status')).toHaveTextContent('Esta persona ya existía en el grupo: se ha añadido a esta empresa');
  expect(onDone).toHaveBeenCalled();
});

test('keptPasswordIsAddedToTheNotice', async () => {
  show({ [path]: () => created(true, true) });
  await fillNames();
  await submit();
  expect(await screen.findByRole('status')).toHaveTextContent(
    'Esta persona ya existía en el grupo: se ha añadido a esta empresa. Conserva su contraseña.');
});

test('failedAddKeepsTheTypedValues', async () => {
  show({ [path]: () => ({ status: 409, body: { detail: 'Esa persona ya pertenece a esta empresa' } }) });
  await fillNames();
  await userEvent.type(screen.getByLabelText('Correo'), 'ana@acme.test');
  await submit();
  expect(await screen.findByRole('alert')).toHaveTextContent('Esa persona ya pertenece a esta empresa');
  expect(screen.getByLabelText('Nombre')).toHaveValue('Ana');
  expect(screen.getByLabelText('Correo')).toHaveValue('ana@acme.test');
});
