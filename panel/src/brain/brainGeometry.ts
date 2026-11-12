export type BrainPart = 'cortex' | 'cerebellum' | 'stem';
export type BrainPoint = { x: number; y: number; z: number; part: BrainPart };
export type BrainGeometry = { points: BrainPoint[]; edges: [number, number][]; neighbors: number[][] };

const GOLDEN_ANGLE = 2.399963;

// Park-Miller; the geometry itself is closed-form, the renderer draws its signals from this.
export function createRandom(seed = 7): () => number {
  let state = seed;
  return () => (state = (state * 16807) % 2147483647) / 2147483647;
}

function hemisphere(side: number, count: number): BrainPoint[] {
  const points: BrainPoint[] = [];
  for (let i = 0; i < count; i++) {
    const v = 1 - ((i + 0.5) / count) * 2, r = Math.sqrt(1 - v * v), th = i * GOLDEN_ANGLE;
    let lx = Math.cos(th) * r, ly = v;
    const lz = Math.sin(th) * r;
    const folds = 1 + 0.07 * Math.sin(9 * lz + 4 * Math.cos(5 * ly)) * Math.sin(8 * ly + 3 * lz) + 0.03 * Math.sin(17 * lz * ly);
    if (lx * side < 0) lx *= 0.85;
    if (ly < 0) ly *= 0.7;
    if (ly < -0.1 && lz > -0.3 && lz < 0.55) ly -= 0.12 * (1 - Math.abs(lz - 0.12) / 0.43);
    const width = 0.55 * (1 - 0.12 * Math.max(0, lz));
    points.push({ x: side * 0.5 + lx * width * folds, y: ly * 0.8 * folds + 0.1, z: lz * 1.15 * folds, part: 'cortex' });
  }
  return points;
}

function cerebellum(count: number): BrainPoint[] {
  return Array.from({ length: count }, (_, i) => {
    const v = 1 - ((i + 0.5) / count) * 2, r = Math.sqrt(1 - v * v), th = i * GOLDEN_ANGLE;
    const folia = 1 + 0.06 * Math.sin(22 * v);
    return { x: Math.cos(th) * r * 0.62 * folia, y: -0.52 + v * 0.27 * folia, z: -0.78 + Math.sin(th) * r * 0.4 * folia, part: 'cerebellum' as const };
  });
}

function stem(count: number): BrainPoint[] {
  return Array.from({ length: count }, (_, i) => {
    const t = i / (count - 1), a = i * GOLDEN_ANGLE;
    return { x: Math.cos(a) * 0.13, y: -0.38 - t * 0.78, z: -0.3 - t * 0.12 + Math.sin(a) * 0.13, part: 'stem' as const };
  });
}

const crossesFissure = (a: BrainPoint, b: BrainPoint) =>
  a.part === 'cortex' && b.part === 'cortex' && Math.sign(a.x) !== Math.sign(b.x);

const distance = (a: BrainPoint, b: BrainPoint) => (a.x - b.x) ** 2 + (a.y - b.y) ** 2 + (a.z - b.z) ** 2;

function nearest(points: BrainPoint[], i: number, count: number): number[] {
  const best: [number, number][] = [];
  const self = points[i]!;
  points.forEach((q, j) => {
    if (i === j || crossesFissure(self, q)) return;
    const d = distance(self, q);
    if (best.length < count || d < best[count - 1]![0]) {
      best.push([d, j]);
      best.sort((a, b) => a[0] - b[0]);
      best.length = Math.min(best.length, count);
    }
  });
  return best.map(([, j]) => j);
}

function centralBridge(points: BrainPoint[]): [number, number][] {
  const medial = points.map((p, i) => ({ p, i }))
    .filter(({ p }) => p.part === 'cortex' && Math.abs(p.x) < 0.12 && p.y > -0.1 && Math.abs(p.z) < 0.6);
  const pairs: [number, number][] = [];
  for (const { p, i } of medial) {
    const other = medial.filter(({ p: q }) => Math.sign(q.x) !== Math.sign(p.x))
      .sort((a, b) => Math.abs(a.p.z - p.z) - Math.abs(b.p.z - p.z))[0];
    if (other) pairs.push([i, other.i]);
  }
  return pairs;
}

function connect(points: BrainPoint[]): Pick<BrainGeometry, 'edges' | 'neighbors'> {
  const edges: [number, number][] = [], seen = new Set<string>(), neighbors: number[][] = points.map(() => []);
  const link = (i: number, j: number) => {
    const key = `${Math.min(i, j)}:${Math.max(i, j)}`;
    if (seen.has(key)) return;
    seen.add(key);
    edges.push([i, j]);
    neighbors[i]!.push(j);
    neighbors[j]!.push(i);
  };
  points.forEach((_, i) => nearest(points, i, 3).forEach((j) => link(i, j)));
  centralBridge(points).forEach(([i, j]) => link(i, j));
  return { edges, neighbors };
}

export function buildBrainGeometry(seed = 7): BrainGeometry {
  void seed;
  const points = [...hemisphere(-1, 230), ...hemisphere(1, 230), ...cerebellum(80), ...stem(28)];
  return { points, ...connect(points) };
}
