import { render, screen } from '@testing-library/react';
import { StatusPill } from './StatusPill';

it('statusPillShowsItsText', () => {
  render(<StatusPill tone="pending">Pendiente</StatusPill>);
  expect(screen.getByText('Pendiente')).toBeInTheDocument();
});
