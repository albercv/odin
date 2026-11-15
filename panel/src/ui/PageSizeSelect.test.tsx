import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PageSizeSelect } from './PageSizeSelect';

it('pageSizeSelectShowsOptionsAndReportsChange', async () => {
  const onChange = vi.fn();
  render(<PageSizeSelect value={10} onChange={onChange} />);
  const select = screen.getByRole('combobox', { name: 'Por página' });
  expect(select).toHaveValue('10');
  await userEvent.selectOptions(select, '25');
  expect(onChange).toHaveBeenCalledWith(25);
});
