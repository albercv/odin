import { describe, expect, it } from 'vitest';
import { createFrameGuard } from './frameGuard';

function feed(guard: ReturnType<typeof createFrameGuard>, ms: number, count: number) {
  let last: 'continue' | 'freeze' = 'continue';
  for (let i = 0; i < count; i++) last = guard.record(ms);
  return last;
}

describe('createFrameGuard', () => {
  it('freezesWhenTheFirstSixtyFramesAverageOverTwelveMs', () => {
    const guard = createFrameGuard();
    expect(feed(guard, 13, 59)).toBe('continue');
    expect(guard.record(13)).toBe('freeze');
  });

  it('keepsRunningUnderBudget', () => {
    const guard = createFrameGuard();
    expect(feed(guard, 5, 60)).toBe('continue');
    expect(feed(guard, 50, 10)).toBe('continue');
  });

  it('decidesOnlyOnce', () => {
    const guard = createFrameGuard();
    feed(guard, 5, 60);
    feed(guard, 50, 200);
    expect(guard.summary()?.averageMs).toBe(5);
  });

  it('summarizesAverageAndMedian', () => {
    const guard = createFrameGuard(12, 4);
    expect(guard.summary()).toBeUndefined();
    [1, 2, 3, 10].forEach((ms) => guard.record(ms));
    expect(guard.summary()).toEqual({ averageMs: 4, medianMs: 2.5 });
  });
});
