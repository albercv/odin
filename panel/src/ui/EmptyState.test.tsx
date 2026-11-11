import { render, screen } from '@testing-library/react';
import { EmptyState } from './EmptyState';

it('emptyStateShowsTitleAndDescription', () => {
  render(<EmptyState title="Nada pendiente" description="Todo al día" />);
  expect(screen.getByText('Nada pendiente')).toBeInTheDocument();
  expect(screen.getByText('Todo al día')).toBeInTheDocument();
});
