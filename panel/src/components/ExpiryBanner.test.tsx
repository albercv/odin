import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { ExpiryBanner } from './ExpiryBanner';

const MIN = 60_000;
const START = Date.parse('2026-11-16T10:00:00Z');
const me = (absoluteInMin: number) => ({ absoluteExpiresAt: new Date(START + absoluteInMin * MIN).toISOString() }) as never;

beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(START); });
afterEach(() => vi.useRealTimers());

const wait = (min: number) => act(() => { vi.advanceTimersByTime(min * MIN); });

test('staysHiddenUntilFiveMinutesBeforeIdleExpiry', () => {
  render(<ExpiryBanner me={me(600)} onRefresh={() => undefined} />);
  wait(24);
  expect(screen.queryByRole('alert')).toBeNull();
  wait(2);
  expect(screen.getByRole('alert')).toHaveTextContent('Tu sesión caduca a las');
  expect(screen.getByRole('button', { name: 'Seguir conectado' })).toBeInTheDocument();
  expect(screen.queryByText('La sesión dura como máximo 12 horas: vuelve a entrar.')).toBeNull();
});

test('keepConnectedAsksTheServerAgain', () => {
  const onRefresh = vi.fn();
  render(<ExpiryBanner me={me(600)} onRefresh={onRefresh} />);
  wait(26);
  fireEvent.click(screen.getByRole('button', { name: 'Seguir conectado' }));
  expect(onRefresh).toHaveBeenCalledOnce();
});

test('whenTheAbsoluteLimitIsCloserItSaysSoAndOffersNoRenewal', () => {
  render(<ExpiryBanner me={me(20)} onRefresh={() => undefined} />);
  wait(14);
  expect(screen.queryByRole('alert')).toBeNull();
  wait(2);
  expect(screen.getByText('La sesión dura como máximo 12 horas: vuelve a entrar.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Seguir conectado' })).toBeNull();
});
