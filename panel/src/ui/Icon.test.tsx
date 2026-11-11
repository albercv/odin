import { render } from '@testing-library/react';
import { Icon } from './Icon';

it('iconIsHiddenFromAssistiveTech', () => {
  const { container } = render(<Icon name="plus" />);
  const svg = container.querySelector('svg')!;
  expect(svg).toHaveAttribute('aria-hidden', 'true');
  expect(svg).toHaveAttribute('viewBox', '0 0 24 24');
});
