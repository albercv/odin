import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pagination } from './Pagination';

it('paginationDisablesPreviousOnFirstAndNextOnLast', () => {
  const { rerender } = render(<Pagination label="Páginas" page={1} pageCount={3} onPage={() => {}} />);
  expect(screen.getByRole('button', { name: 'Anterior' })).toHaveAttribute('aria-disabled', 'true');
  expect(screen.getByRole('button', { name: 'Siguiente' })).not.toHaveAttribute('aria-disabled', 'true');
  expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
  rerender(<Pagination label="Páginas" page={3} pageCount={3} onPage={() => {}} />);
  expect(screen.getByRole('button', { name: 'Siguiente' })).toHaveAttribute('aria-disabled', 'true');
});

it('paginationDisabledButtonDoesNotCallOnPage', async () => {
  const onPage = vi.fn();
  render(<Pagination label="Páginas" page={1} pageCount={2} onPage={onPage} />);
  await userEvent.click(screen.getByRole('button', { name: 'Anterior' }));
  expect(onPage).not.toHaveBeenCalled();
});

it('paginationKeepsFocusOnNextWhenReachingLastPage', async () => {
  function Host() {
    const [page, setPage] = useState(1);
    return <Pagination label="Páginas" page={page} pageCount={2} onPage={setPage} />;
  }
  render(<Host />);
  const next = screen.getByRole('button', { name: 'Siguiente' });
  await userEvent.click(next);
  expect(next).toHaveAttribute('aria-disabled', 'true');
  expect(next).toHaveFocus();
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
