import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach } from 'vitest';
import { ConfirmProvider, useConfirm } from './ConfirmDialog';
import { mockDialog } from './dialogMock';

function Harness({ danger }: { danger?: boolean }) {
  const confirm = useConfirm();
  const [result, setResult] = useState('none');
  const ask = async () => setResult(String(await confirm({ message: '¿Eliminar a Marta?', confirmLabel: 'Eliminar', danger })));
  return <><button onClick={ask}>Abrir</button><output>{result}</output></>;
}

const setup = (danger?: boolean) => render(<ConfirmProvider><Harness danger={danger} /></ConfirmProvider>);
beforeEach(mockDialog);

it('confirmResolvesTrueOnConfirm', async () => {
  setup(true);
  await userEvent.click(screen.getByText('Abrir'));
  expect(screen.getByText('¿Eliminar a Marta?')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
  expect(await screen.findByText('true')).toBeInTheDocument();
});

it('confirmResolvesFalseOnEscapeAndCancel', async () => {
  setup();
  await userEvent.click(screen.getByText('Abrir'));
  fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
  expect(await screen.findByText('false')).toBeInTheDocument();
  await userEvent.click(screen.getByText('Abrir'));
  await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
  expect(await screen.findByText('false')).toBeInTheDocument();
});

it('confirmReturnsFocus', async () => {
  setup();
  const opener = screen.getByText('Abrir');
  await userEvent.click(opener);
  expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
  await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
  expect(opener).toHaveFocus();
});
