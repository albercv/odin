import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { Button } from './Button';
import { IconButton } from './IconButton';

it('buttonVariantsKeepTheNativeButton', async () => {
  const onClick = vi.fn();
  render(<><Button variant="danger" icon="plus" onClick={onClick}>Borrar</Button><Button disabled>Quieto</Button></>);
  const button = screen.getByRole('button', { name: 'Borrar' });
  expect(button).toHaveAttribute('type', 'button');
  await userEvent.click(button);
  expect(onClick).toHaveBeenCalledOnce();
  expect(screen.getByRole('button', { name: 'Quieto' })).toBeDisabled();
});

it('iconButtonUsesTheLabelAsAccessibleName', () => {
  render(<IconButton icon="more" label="Más acciones de Ana" />);
  expect(screen.getByRole('button', { name: 'Más acciones de Ana' })).toBeInTheDocument();
});
