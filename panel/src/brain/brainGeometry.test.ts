import { describe, expect, it } from 'vitest';
import { buildBrainGeometry } from './brainGeometry';

describe('buildBrainGeometry', () => {
  it('isDeterministicForTheSameSeed', () => {
    expect(buildBrainGeometry(7)).toEqual(buildBrainGeometry(7));
    expect(buildBrainGeometry()).toEqual(buildBrainGeometry(7));
  });

  it('hasTheExpectedPointCount', () => {
    const { points } = buildBrainGeometry();
    expect(points).toHaveLength(568);
    expect(points.filter((p) => p.part === 'cortex')).toHaveLength(460);
    expect(points.filter((p) => p.part === 'cerebellum')).toHaveLength(80);
    expect(points.filter((p) => p.part === 'stem')).toHaveLength(28);
  });

  it('everyPointHasAConnection', () => {
    const { points, neighbors } = buildBrainGeometry();
    expect(neighbors).toHaveLength(points.length);
    expect(neighbors.every((list) => list.length >= 1)).toBe(true);
  });

  it('onlyTheCentralBridgeCrossesTheFissure', () => {
    const { points, edges } = buildBrainGeometry();
    const at = (i: number) => points[i]!;
    const crossing = edges.filter(([a, b]) =>
      at(a).part === 'cortex' && at(b).part === 'cortex' && Math.sign(at(a).x) !== Math.sign(at(b).x));
    expect(crossing.length).toBeGreaterThan(0);
    for (const p of crossing.flatMap(([a, b]) => [at(a), at(b)])) {
      expect(Math.abs(p.x)).toBeLessThan(0.12);
      expect(p.y).toBeGreaterThan(-0.1);
      expect(Math.abs(p.z)).toBeLessThan(0.6);
    }
  });
});
