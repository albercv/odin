import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { ConfirmProvider } from './ConfirmDialog';
import { ToastProvider } from './Toast';
import { mockDialog } from './dialogMock';

export function renderWithProviders(ui: ReactElement) {
  mockDialog();
  return render(<ToastProvider><ConfirmProvider>{ui}</ConfirmProvider></ToastProvider>);
}
