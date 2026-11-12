import { render, screen } from '@testing-library/react';
import { Capabilities } from './Capabilities';

const company = { id: 'c1', name: 'Acme', code: 'ACM', capabilities: ['SEARCH_KNOWLEDGE', 'SIMILAR_CASES'] } as never;

test('showsSimilarCasesCapability', () => {
  const { rerender } = render(<Capabilities company={company} />);
  expect(screen.getByText('Casos parecidos · activa')).toBeInTheDocument();
  rerender(<Capabilities company={company} onChange={() => undefined} />);
  expect(screen.getByRole('switch', { name: 'Casos parecidos' })).toBeChecked();
});
