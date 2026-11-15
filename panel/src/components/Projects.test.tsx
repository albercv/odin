import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { Projects } from './Projects';
import { ProjectTermsContext, projectTerms } from './projectTerms';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const obra = { id: 'p1', companyId: 'c1', name: 'Obra', restricted: true, memberCount: 0 };
const list = 'GET /v1/admin/companies/c1/projects';
const one = '/v1/admin/companies/c1/projects/p1';

const actions = async (name: string) => {
  const toggle = await screen.findByRole('button', { name: `Más acciones de ${name}` });
  if (toggle.getAttribute('aria-expanded') !== 'true') await userEvent.click(toggle);
};

const show = (routes: Routes) => {
  const calls = mockFetch(routes);
  renderWithProviders(<Projects companyId="c1" />);
  return calls;
};

test('renamesProject', async () => {
  const calls = show({ [list]: () => [obra], [`PATCH ${one}`]: () => ({ ...obra, name: 'Reforma' }) });
  await actions('Obra');
  await userEvent.click(screen.getByRole('button', { name: 'Renombrar Obra' }));
  await userEvent.clear(screen.getByLabelText('Nombre'));
  await userEvent.type(screen.getByLabelText('Nombre'), 'Reforma');
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Obra' }));
  await waitFor(() => expect(calls.find((c) => c.key === `PATCH ${one}`)?.body).toEqual({ name: 'Reforma' }));
});

test('rowButtonsShowShortTextAndNameTheTarget', async () => {
  show({ [list]: () => [obra] });
  await actions('Obra');
  [['Renombrar', 'Renombrar Obra'], ['Miembros', 'Miembros de Obra'], ['Borrar', 'Borrar Obra']].forEach(([visible, name]) =>
    expect(screen.getByRole('button', { name })).toHaveTextContent(new RegExp(`^${visible}$`)));
});

test('inlineRenameButtonsShowShortTextAndNameTheTarget', async () => {
  show({ [list]: () => [obra] });
  await actions('Obra');
  await userEvent.click(screen.getByRole('button', { name: 'Renombrar Obra' }));
  expect(screen.getByRole('button', { name: 'Guardar Obra' })).toHaveTextContent(/^Guardar$/);
  expect(screen.getByRole('button', { name: 'Cancelar Obra' })).toHaveTextContent(/^Cancelar$/);
});

test('deleteAsksWithTheConfirmDialog', async () => {
  const calls = show({ [list]: () => [obra], [`DELETE ${one}`]: () => ({ status: 204 }) });
  await actions('Obra');
  await userEvent.click(screen.getByRole('button', { name: 'Borrar Obra' }));
  const dialog = screen.getByRole('dialog', { name: '¿Borrar el proyecto Obra? Sus documentos se conservan.' });
  await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
  expect(calls.some((c) => c.key.startsWith('DELETE'))).toBe(false);
  await userEvent.click(screen.getByRole('button', { name: 'Borrar Obra' }));
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Borrar' }));
  await waitFor(() => expect(calls.some((c) => c.key === `DELETE ${one}`)).toBe(true));
});

test('duplicateNameShowsTheApiMessage', async () => {
  show({
    [list]: () => [obra],
    [`PATCH ${one}`]: () => ({ status: 409, body: { detail: 'Ya existe un proyecto con ese nombre' } }),
  });
  await actions('Obra');
  await userEvent.click(screen.getByRole('button', { name: 'Renombrar Obra' }));
  await userEvent.click(screen.getByRole('button', { name: 'Guardar Obra' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Ya existe un proyecto con ese nombre');
  expect(screen.getByLabelText('Nombre')).toHaveValue('Obra');
});

test('failedAddKeepsTheTypedName', async () => {
  show({
    [list]: () => [],
    'POST /v1/admin/companies/c1/projects': () => ({ status: 409, body: { detail: 'Ya existe un proyecto con ese nombre' } }),
  });
  await userEvent.click(await screen.findByRole('button', { name: 'Nuevo proyecto' }));
  await userEvent.type(screen.getByLabelText('Nombre del proyecto'), 'Obra');
  await userEvent.click(screen.getByRole('button', { name: 'Crear proyecto' }));
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  expect(screen.getByLabelText('Nombre del proyecto')).toHaveValue('Obra');
});

const many = Array.from({ length: 23 }, (_, i) => ({ id: `m${i}`, companyId: 'c1', name: i < 3 ? `Solar ${i}` : `Obra ${i}`, restricted: true, memberCount: 0 }));
const search = (q: string) => fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar proyectos' }), { target: { value: q } });
const bodyRows = () => screen.getAllByRole('row').slice(1);

test('showsTenPerPageWithPagination', async () => {
  show({ [list]: () => many });
  await screen.findByText('Obra 3');
  expect(bodyRows()).toHaveLength(10);
  const nav = screen.getByRole('navigation', { name: 'Páginas de proyectos' });
  expect(nav).toHaveTextContent('Página 1 de 3');
  await userEvent.click(within(nav).getByRole('button', { name: 'Siguiente' }));
  expect(nav).toHaveTextContent('Página 2 de 3');
});

test('searchFiltersAndGoesBackToPageOne', async () => {
  show({ [list]: () => many });
  await screen.findByText('Obra 3');
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  search('sol');
  await waitFor(() => expect(bodyRows()).toHaveLength(3));
  expect(screen.getByText('3 resultados')).toBeInTheDocument();
  expect(screen.queryByRole('navigation', { name: 'Páginas de proyectos' })).not.toBeInTheDocument();
});

test('shortQueryDoesNotFilter', async () => {
  show({ [list]: () => many });
  await screen.findByText('Obra 3');
  search('so');
  await new Promise((r) => setTimeout(r, 400));
  expect(bodyRows()).toHaveLength(10);
});

test('creatingKeepsTheSearch', async () => {
  const calls = show({ [list]: () => many, 'POST /v1/admin/companies/c1/projects': () => ({ ...obra, id: 'new', name: 'Solana' }) });
  await screen.findByText('Obra 3');
  search('sol');
  await waitFor(() => expect(bodyRows()).toHaveLength(3));
  await userEvent.click(screen.getByRole('button', { name: 'Nuevo proyecto' }));
  await userEvent.type(screen.getByLabelText('Nombre del proyecto'), 'Solana');
  await userEvent.click(screen.getByRole('button', { name: 'Crear proyecto' }));
  await waitFor(() => expect(calls.filter((c) => c.key === list)).toHaveLength(2));
  expect(screen.getByRole('searchbox', { name: 'Buscar proyectos' })).toHaveValue('sol');
  expect(bodyRows()).toHaveLength(3);
});

test('usesTheCompanyWordForProjects', async () => {
  mockFetch({ [list]: () => [obra] });
  const terms = projectTerms({ singular: 'comunidad', plural: 'comunidades', feminine: true });
  renderWithProviders(<ProjectTermsContext.Provider value={terms}><Projects companyId="c1" /></ProjectTermsContext.Provider>);
  expect(await screen.findByRole('heading', { name: 'Comunidades' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Nueva comunidad' })).toBeInTheDocument();
  expect(screen.getByRole('searchbox', { name: 'Buscar comunidades' })).toBeInTheDocument();
  expect(screen.getByText('Una comunidad nueva nace cerrada: solo la ven sus miembros.')).toBeInTheDocument();
});
