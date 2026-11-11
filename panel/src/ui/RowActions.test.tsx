import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RowActions } from './RowActions';

const setup = () => render(<RowActions label="Acciones de Marta Ibáñez"><button>Editar Marta Ibáñez</button></RowActions>);

it('rowActionsTogglesWithAriaExpanded', async () => {
  setup();
  const toggle = screen.getByRole('button', { name: 'Acciones de Marta Ibáñez' });
  expect(toggle).toHaveAttribute('aria-expanded', 'false');
  expect(screen.queryByText('Editar Marta Ibáñez')).not.toBeInTheDocument();
  await userEvent.click(toggle);
  expect(toggle).toHaveAttribute('aria-expanded', 'true');
  expect(document.getElementById(toggle.getAttribute('aria-controls')!)).toContainElement(screen.getByText('Editar Marta Ibáñez'));
});

it('rowActionsClosesOnEscape', async () => {
  setup();
  const toggle = screen.getByRole('button', { name: 'Acciones de Marta Ibáñez' });
  await userEvent.click(toggle);
  await userEvent.click(screen.getByText('Editar Marta Ibáñez'));
  await userEvent.keyboard('{Escape}');
  expect(toggle).toHaveAttribute('aria-expanded', 'false');
  expect(toggle).toHaveFocus();
});
