import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pagination } from './Pagination';

it('paginationDisablesPreviousOnFirstAndNextOnLast', () => {
  const { rerender } = render(<Pagination label="Páginas" page={1} pageCount={3} onPage={() => {}} />);
  expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled();
  expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
  rerender(<Pagination label="Páginas" page={3} pageCount={3} onPage={() => {}} />);
  expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
});

it('paginationCallsOnPage', async () => {
  const onPage = vi.fn();
  render(<Pagination label="Páginas" page={2} pageCount={3} onPage={onPage} />);
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  expect(onPage).toHaveBeenCalledWith(3);
  await userEvent.click(screen.getByRole('button', { name: 'Anterior' }));
  expect(onPage).toHaveBeenCalledWith(1);
});

it('paginationHiddenWithSinglePage', () => {
  render(<Pagination label="Páginas" page={1} pageCount={1} onPage={() => {}} />);
  expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
});
