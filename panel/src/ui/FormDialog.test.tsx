import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach } from 'vitest';
import { FormDialog } from './FormDialog';
import { mockDialog } from './dialogMock';

function Harness() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Abrir</button>
      <FormDialog title="Nueva persona" open={open} onClose={() => setOpen(false)}>
        <form><label>Nombre<input /></label><label>Correo<input /></label></form>
      </FormDialog>
    </>
  );
}
beforeEach(mockDialog);

it('formDialogFocusesTheFirstFieldAndReturnsFocus', async () => {
  render(<Harness />);
  const opener = screen.getByText('Abrir');
  await userEvent.click(opener);
  expect(screen.getByRole('heading', { level: 2, name: 'Nueva persona' })).toBeInTheDocument();
  expect(screen.getByLabelText('Nombre')).toHaveFocus();
  await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(opener).toHaveFocus();
});

it('formDialogNotDismissibleHasNoCloseAndIgnoresCancel', () => {
  const onClose = vi.fn();
  render(<FormDialog title="Clave nueva" open dismissible={false} onClose={onClose}><p>Contenido</p></FormDialog>);
  expect(screen.queryByRole('button', { name: 'Cerrar' })).toBeNull();
  fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }));
  expect(onClose).not.toHaveBeenCalled();
});

it('formDialogNotDismissibleIgnoresEscapeAndReopensAfterNativeClose', () => {
  render(<FormDialog title="Clave nueva" open dismissible={false} onClose={() => {}}><p>Contenido</p></FormDialog>);
  const dialog = screen.getByRole('dialog');
  expect(fireEvent.keyDown(dialog, { key: 'Escape' })).toBe(false);
  const showModal = HTMLDialogElement.prototype.showModal as ReturnType<typeof vi.fn>;
  showModal.mockClear();
  dialog.removeAttribute('open');
  fireEvent(dialog, new Event('close'));
  expect(showModal).toHaveBeenCalledTimes(1);
});
