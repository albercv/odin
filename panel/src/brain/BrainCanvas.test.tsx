import { render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BrainCanvas } from './BrainCanvas';
import { COVER_PLACEMENT } from './brainRenderer';

let callbacks: Map<number, FrameRequestCallback>;
let nextId: number;
let fills: number;
let visibility: string;
let frameCost: number;
let reduced: boolean;

function runFrame(now: number) {
  const pending = [...callbacks.entries()];
  callbacks.clear();
  pending.forEach(([, cb]) => cb(now));
}

beforeEach(() => {
  callbacks = new Map();
  nextId = 1;
  fills = 0;
  visibility = 'visible';
  frameCost = 1;
  reduced = false;
  const ctx = new Proxy({}, { get: (_t, name) => (name === 'fill' ? () => { fills++; } : () => undefined), set: () => true });
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D);
  vi.spyOn(HTMLCanvasElement.prototype, 'getBoundingClientRect').mockReturnValue({ width: 400, height: 300 } as DOMRect);
  vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => { callbacks.set(nextId, cb); return nextId++; });
  vi.stubGlobal('cancelAnimationFrame', (id: number) => { callbacks.delete(id); });
  vi.stubGlobal('matchMedia', () => ({ matches: reduced }));
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} });
  vi.stubGlobal('IntersectionObserver', class { observe() {} disconnect() {} });
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility as DocumentVisibilityState);
  let clock = 0;
  vi.spyOn(performance, 'now').mockImplementation(() => (clock += frameCost));
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('BrainCanvas', () => {
  it('leavesTheGradientWhenTheCanvasHasNoContext', () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    expect(() => render(<BrainCanvas placement={COVER_PLACEMENT} />)).not.toThrow();
    expect(callbacks.size).toBe(0);
  });

  it('drawsOneStillFrameWhenReducedMotion', () => {
    reduced = true;
    render(<BrainCanvas placement={COVER_PLACEMENT} />);
    expect(callbacks.size).toBe(1);
    runFrame(16);
    expect(callbacks.size).toBe(0);
    expect(fills).toBeGreaterThan(0);
  });

  it('drawsStillOnLowPowerDevices', () => {
    vi.spyOn(navigator, 'hardwareConcurrency', 'get').mockReturnValue(2);
    render(<BrainCanvas placement={COVER_PLACEMENT} />);
    runFrame(16);
    expect(callbacks.size).toBe(0);
    expect(fills).toBeGreaterThan(0);
  });

  it('stopsTheLoopOnUnmount', () => {
    const { unmount } = render(<BrainCanvas placement={COVER_PLACEMENT} />);
    runFrame(16);
    expect(callbacks.size).toBe(1);
    unmount();
    expect(callbacks.size).toBe(0);
  });

  it('pausesWhileTheTabIsHidden', () => {
    render(<BrainCanvas placement={COVER_PLACEMENT} />);
    runFrame(16);
    visibility = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(callbacks.size).toBe(0);
    visibility = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(callbacks.size).toBe(1);
  });

  it('freezesWhenTheGuardSaysSo', () => {
    frameCost = 20;
    const { container } = render(<BrainCanvas placement={COVER_PLACEMENT} />);
    for (let i = 0; i < 61; i++) runFrame(16 * (i + 1));
    expect(callbacks.size).toBe(0);
    expect(container.querySelector('canvas')?.dataset.averageFrameMs).toBeDefined();
  });
});
