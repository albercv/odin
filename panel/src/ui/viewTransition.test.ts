import { afterEach, expect, it, vi } from 'vitest';
import { withViewTransition } from './viewTransition';

const doc = document as unknown as { startViewTransition?: unknown };
const setMotion = (reduce: boolean) => vi.stubGlobal('matchMedia', () => ({ matches: reduce }));
afterEach(() => { delete doc.startViewTransition; vi.unstubAllGlobals(); });

it('viewTransitionFallsBackWithoutSupport', () => {
  setMotion(false);
  const update = vi.fn();
  withViewTransition(update);
  expect(update).toHaveBeenCalledTimes(1);
});

it('viewTransitionSkippedWithReducedMotion', () => {
  setMotion(true);
  doc.startViewTransition = vi.fn();
  const update = vi.fn();
  withViewTransition(update);
  expect(doc.startViewTransition).not.toHaveBeenCalled();
  expect(update).toHaveBeenCalledTimes(1);
});

it('viewTransitionUsedWhenSupported', () => {
  setMotion(false);
  doc.startViewTransition = vi.fn((callback: () => void) => callback());
  const update = vi.fn();
  withViewTransition(update);
  expect(doc.startViewTransition).toHaveBeenCalledTimes(1);
  expect(update).toHaveBeenCalledTimes(1);
});
