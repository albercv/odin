import { render, screen } from '@testing-library/react';
import { Table, Td, Tr } from './Table';

test('tableKeepsItsSemanticsEvenWhenCssTurnsRowsIntoBlocks', () => {
  render(<Table columns={['Nombre', 'Correo']}><Tr><Td label="Nombre">Ana</Td><Td label="Correo">ana@e.test</Td></Tr></Table>);
  expect(screen.getByRole('table')).toHaveAttribute('role', 'table');
  expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Nombre', 'Correo', 'Acciones']);
  expect(screen.getAllByRole('row')).toHaveLength(2);
  expect(screen.getAllByRole('cell')).toHaveLength(2);
  expect(screen.getByRole('cell', { name: 'Ana' })).toHaveAttribute('data-label', 'Nombre');
  expect(screen.getAllByRole('rowgroup')).toHaveLength(2);
});
