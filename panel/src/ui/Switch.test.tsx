import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { Switch } from './Switch';

it('switchHasSwitchRoleAndToggles', async () => {
  const onChange = vi.fn();
  render(<Switch label="Ver todo" checked={false} onChange={onChange} />);
  const toggle = screen.getByRole('switch', { name: 'Ver todo' });
  expect(toggle).not.toBeChecked();
  await userEvent.click(toggle);
  expect(onChange).toHaveBeenCalledWith(true);
});
