import { flushSync } from 'react-dom';

type Transitioning = Document & { startViewTransition?: (update: () => void) => unknown };

export function withViewTransition(update: () => void): void {
  const doc = document as Transitioning;
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (!doc.startViewTransition || reduced) return update();
  doc.startViewTransition(() => flushSync(update));
}
