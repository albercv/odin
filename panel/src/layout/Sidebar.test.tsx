import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { Sidebar } from './Sidebar';

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
    expect(screen.queryByRole('link', { name: 'Empresas' })).toBeNull();
    expect(screen.getByRole('link', { name: 'Empresa' })).toBeInTheDocument();
    expect(screen.getByText('Acme')).toBeInTheDocument();
  });

  it('sidebarShowsCompaniesForGroupAdmins', async () => {
    const { setPage } = show({ isGroup: true });
    await userEvent.click(screen.getByRole('link', { name: 'Empresas' }));
    expect(setPage).toHaveBeenCalledWith('companies');
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
