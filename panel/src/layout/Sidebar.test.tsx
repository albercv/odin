import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { Sidebar } from './Sidebar';

const acme = { id: 'c1', name: 'Acme', code: 'ACM', codeReviewed: true, projectLabel: { singular: 'proyecto', plural: 'proyectos', feminine: false } };
const beta = { id: 'c2', name: 'Beta', code: 'BET', codeReviewed: true, projectLabel: { singular: 'proyecto', plural: 'proyectos', feminine: false } };

type Props = Partial<React.ComponentProps<typeof Sidebar>>;
const show = (props: Props = {}) => {
  const setPage = vi.fn();
  const onLogout = vi.fn();
  render(<Sidebar isGroup={false} name="Bea" companyName="Acme" page="company" pending={0} setPage={setPage} onLogout={onLogout} {...props} />);
  return { setPage, onLogout };
};

describe('Sidebar', () => {
  it('sidebarHidesCompaniesForCompanyAdmins', () => {
    show();
    expect(screen.queryByRole('link', { name: 'Grupo' })).toBeNull();
    expect(screen.getByRole('link', { name: 'Empresa' })).toBeInTheDocument();
    expect(screen.getByText('Acme')).toBeInTheDocument();
  });

  it('sidebarShowsCompaniesForGroupAdmins', async () => {
    const { setPage } = show({ isGroup: true });
    await userEvent.click(screen.getByRole('link', { name: 'Grupo' }));
    expect(setPage).toHaveBeenCalledWith('companies');
  });

  it('menuSaysGrupo', () => {
    show({ isGroup: true });
    expect(screen.getByRole('link', { name: 'Grupo' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Empresas' })).toBeNull();
  });

  it('groupAdminAlwaysSeesTheSwitcherWithAllCompanies', () => {
    const { unmount } = render(<Sidebar isGroup name="Bea" page="companies" pending={0} setPage={() => {}} onLogout={() => {}} switcher={{ companies: [acme], currentId: 'c1', onChange: () => {} }} />);
    expect(screen.getByRole('combobox', { name: 'Empresa' })).toHaveValue('c1');
    unmount();
    show({ isGroup: true, switcher: { companies: [acme, beta], currentId: 'c2', onChange: () => {} } });
    const select = screen.getByRole('combobox', { name: 'Empresa' });
    expect(select).toHaveValue('c2');
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual(['Acme', 'Beta']);
  });

  it('neverOffersAnEmptyOption', () => {
    show({ isGroup: true, switcher: { companies: [acme, beta], currentId: 'c1', onChange: () => {} } });
    expect(screen.queryByRole('option', { name: 'Elige una empresa' })).toBeNull();
  });

  it('companyAdminWithOneCompanyShowsTheNameNotTheSwitcher', () => {
    show({ switcher: { companies: [acme], currentId: 'c1', onChange: () => {} } });
    expect(screen.queryByRole('combobox')).toBeNull();
    expect(screen.getByText('Acme')).toBeInTheDocument();
  });

  it('groupAdminsLinkOnlyForTheGroupAdmin', async () => {
    const { unmount } = render(<Sidebar isGroup={false} name="Bea" page="company" pending={0} setPage={() => {}} onLogout={() => {}} />);
    expect(screen.queryByRole('link', { name: 'Administración del grupo' })).toBeNull();
    unmount();
    const { setPage } = show({ isGroup: true });
    await userEvent.click(screen.getByRole('link', { name: 'Administración del grupo' }));
    expect(setPage).toHaveBeenCalledWith('groupAdmins');
  });

  it('sidebarMarksTheCurrentPage', () => {
    show({ page: 'documents' });
    expect(screen.getByRole('link', { name: 'Documentos' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Empresa' })).not.toHaveAttribute('aria-current');
  });

  it('pendingCountHiddenAtZero', () => {
    const { unmount } = render(<Sidebar isGroup={false} name="Bea" page="company" pending={0} setPage={() => {}} onLogout={() => {}} />);
    expect(screen.getByRole('link', { name: 'Revisión' })).toBeInTheDocument();
    expect(screen.queryByText('0')).toBeNull();
    unmount();
    show({ pending: 4 });
    expect(screen.getByRole('link', { name: /Revisión/ })).toHaveTextContent('4');
    expect(screen.getByRole('link', { name: 'Revisión, 4 fichas pendientes' })).toBeInTheDocument();
  });

  it('offersAccountAndLogout', async () => {
    const { setPage, onLogout } = show();
    expect(screen.getByText('Bea')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Mi cuenta' }));
    expect(setPage).toHaveBeenCalledWith('account');
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }));
    expect(onLogout).toHaveBeenCalled();
  });
});
