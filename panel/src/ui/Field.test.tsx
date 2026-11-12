import { render, screen } from '@testing-library/react';
import { Field } from './Field';

it('fieldLabelWrapsTheControl', () => {
  render(<Field label="Correo"><input /></Field>);
  expect(screen.getByLabelText('Correo').tagName).toBe('INPUT');
});

it('fieldShowsTheErrorAsAlert', () => {
  render(<Field label="Correo" error="Falta el correo"><input /></Field>);
  expect(screen.getByRole('alert')).toHaveTextContent('Falta el correo');
});
