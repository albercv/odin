import { render, screen } from '@testing-library/react';
import { PageHeader } from './PageHeader';

it('pageHeaderIsTheOnlyH1', () => {
  render(<PageHeader title="Empresa" description="Resumen" actions={<button>Nueva</button>} />);
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  expect(screen.getByRole('heading', { level: 1, name: 'Empresa' })).toBeInTheDocument();
  expect(screen.getByText('Resumen')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Nueva' })).toBeInTheDocument();
});
