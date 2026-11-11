import { useEffect, useRef } from 'react';
import { buildBrainGeometry, type BrainGeometry } from './brainGeometry';
import { createBrainRenderer, type BrainPlacement } from './brainRenderer';
import { createFrameGuard, type FrameSummary } from './frameGuard';
import styles from './BrainCanvas.module.css';

type NetworkInformationLike = { saveData?: boolean };

let geometry: BrainGeometry | undefined;

function isStill(): boolean {
  const nav = navigator as Navigator & { connection?: NetworkInformationLike };
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    || (nav.hardwareConcurrency || 4) <= 2 || Boolean(nav.connection?.saveData);
}

function reportOnce(canvas: HTMLCanvasElement, summary: FrameSummary | undefined) {
  if (!summary || canvas.dataset.averageFrameMs) return;
  canvas.dataset.averageFrameMs = summary.averageMs.toFixed(2);
  canvas.dataset.medianFrameMs = summary.medianMs.toFixed(2);
}

function createLoop(canvas: HTMLCanvasElement, renderer: ReturnType<typeof createBrainRenderer>) {
  const guard = createFrameGuard();
  let raf = 0, last = 0, frozen = false;
  const state = { onScreen: true };

  const stop = () => { cancelAnimationFrame(raf); raf = 0; };

  function tick(now: number) {
    raf = requestAnimationFrame(tick);
    const begin = performance.now();
    renderer.drawFrame(last ? now - last : 0);
    last = now;
    const verdict = guard.record(performance.now() - begin);
    reportOnce(canvas, guard.summary());
    if (verdict !== 'freeze') return;
    frozen = true;
    stop();
    renderer.drawStill();
  }

  function sync() {
    if (frozen || !state.onScreen || document.visibilityState === 'hidden') return stop();
    if (raf) return;
    last = 0;
    raf = requestAnimationFrame(tick);
  }

  return { state, stop, sync };
}

function start(canvas: HTMLCanvasElement, placement: BrainPlacement): () => void {
  geometry ??= buildBrainGeometry();
  const renderer = createBrainRenderer(canvas, geometry, placement);
  const accent = getComputedStyle(document.documentElement).getPropertyValue('--brand-accent-on-dark').trim();
  if (accent) renderer.setColor(accent);
  renderer.resize();
  const resizeObserver = new ResizeObserver(() => renderer.resize());
  resizeObserver.observe(canvas);
  const loop = createLoop(canvas, renderer);
  const cleanup = () => { loop.stop(); resizeObserver.disconnect(); };
  if (isStill()) {
    requestAnimationFrame(() => renderer.drawStill());
    return cleanup;
  }
  const intersection = new IntersectionObserver((entries) => { loop.state.onScreen = entries[0]?.isIntersecting ?? true; loop.sync(); });
  intersection.observe(canvas);
  document.addEventListener('visibilitychange', loop.sync);
  loop.sync();
  return () => { cleanup(); intersection.disconnect(); document.removeEventListener('visibilitychange', loop.sync); };
}

export function BrainCanvas({ placement }: { placement: BrainPlacement }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => (ref.current ? start(ref.current, placement) : undefined), [placement]);
  return (
    <div className={styles.brain}>
      <canvas ref={ref} className={styles.canvas} aria-hidden="true" />
    </div>
  );
}
