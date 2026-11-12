import { render, screen } from '@testing-library/react';
import { Section } from './Section';

it('sectionHeadingLabelsTheSection', () => {
  render(<Section title="Personas" description="Quién entra" actions={<button>Añadir</button>}>contenido</Section>);
  expect(screen.getByRole('region', { name: 'Personas' })).toHaveTextContent('contenido');
  expect(screen.getByRole('heading', { level: 2, name: 'Personas' })).toBeInTheDocument();
  expect(screen.getByText('Quién entra')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Añadir' })).toBeInTheDocument();
});
