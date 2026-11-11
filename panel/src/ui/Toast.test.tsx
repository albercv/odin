import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { ToastProvider, useToast } from './Toast';

function Harness() {
  const toast = useToast();
  return <><button onClick={() => toast('Ficha aprobada')}>A</button><button onClick={() => toast('Persona eliminada')}>B</button></>;
}
const press = (name: string) => act(() => screen.getByText(name).click());
beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { vi.useRealTimers(); });

it('toastAnnouncesAndClosesAfter2600ms', () => {
  render(<ToastProvider><Harness /></ToastProvider>);
  const region = screen.getByRole('status');
  expect(region).toHaveAttribute('aria-live', 'polite');
  press('A');
  expect(region).toHaveTextContent('Ficha aprobada');
  act(() => { vi.advanceTimersByTime(2599); });
  expect(region).toHaveTextContent('Ficha aprobada');
  act(() => { vi.advanceTimersByTime(1); });
  expect(region).toBeEmptyDOMElement();
});

it('newToastReplacesThePrevious', () => {
  render(<ToastProvider><Harness /></ToastProvider>);
  press('A');
  act(() => { vi.advanceTimersByTime(2000); });
  press('B');
  expect(screen.getAllByRole('status')).toHaveLength(1);
  expect(screen.getByRole('status')).toHaveTextContent('Persona eliminada');
  expect(screen.getByRole('status')).not.toHaveTextContent('Ficha aprobada');
  act(() => { vi.advanceTimersByTime(2000); });
  expect(screen.getByRole('status')).toHaveTextContent('Persona eliminada');
  act(() => { vi.advanceTimersByTime(600); });
  expect(screen.getByRole('status')).toBeEmptyDOMElement();
});
