import { createRandom, type BrainGeometry } from './brainGeometry';

export type BrainPlacement = { centerX: number; centerY: number; widthShare: number; heightShare: number };
export const LOGIN_PLACEMENT: BrainPlacement = { centerX: 0.5, centerY: 0.36, widthShare: 0.27, heightShare: 0.3 };
export const COVER_PLACEMENT: BrainPlacement = { centerX: 0.5, centerY: 0.31, widthShare: 0.22, heightShare: 0.25 };

type Pulse = { from: number; to: number; t: number };
type Projected = [number, number, number];

const MAX_PULSES = 60, SPAWN_EVERY_MS = 180, TRAVEL_MS = 380, MAX_STEP_MS = 50;
const near = (z: number) => Math.max(0, Math.min(1, (1.3 - z) / 2.6));

function toRgba(hex: string, alpha: number): string {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${alpha})`;
}

class BrainDrawing {
  private readonly ctx: CanvasRenderingContext2D;
  private readonly rand = createRandom(7);
  private readonly pulses: Pulse[] = [];
  private readonly flash: Float32Array;
  private accent = '#ffffff';
  private width = 0;
  private height = 0;
  private clock = 0;
  private spawnAt = 0;

  constructor(private readonly canvas: HTMLCanvasElement, private readonly geometry: BrainGeometry,
    private readonly placement: BrainPlacement) {
    this.ctx = canvas.getContext('2d') as CanvasRenderingContext2D;
    this.flash = new Float32Array(geometry.points.length);
  }

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2), rect = this.canvas.getBoundingClientRect();
    this.width = rect.width;
    this.height = rect.height;
    this.canvas.width = this.width * dpr;
    this.canvas.height = this.height * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.draw();
  }

  setColor(hex: string) {
    this.accent = hex;
    this.draw();
  }

  step(deltaMs: number) {
    const dt = Math.min(deltaMs, MAX_STEP_MS);
    this.clock += dt;
    if (this.clock > this.spawnAt) {
      this.fire(Math.floor(this.rand() * this.geometry.points.length));
      this.spawnAt = this.clock + SPAWN_EVERY_MS;
    }
    this.advancePulses(dt);
    const decay = Math.pow(0.0025, dt / 1000);
    for (let i = 0; i < this.flash.length; i++) this.flash[i]! *= decay;
    this.draw();
  }

  draw() {
    if (!this.width) return;
    this.ctx.clearRect(0, 0, this.width, this.height);
    const projected = this.project();
    this.drawEdges(projected);
    this.drawPoints(projected);
    this.drawPulses(projected);
  }

  private project(): Projected[] {
    const yaw = -Math.PI / 2 + 0.6 * Math.sin(this.clock * 0.00022), tilt = 0.22;
    const cy = Math.cos(yaw), sy = Math.sin(yaw), ct = Math.cos(tilt), st = Math.sin(tilt);
    const { widthShare, heightShare, centerX, centerY } = this.placement;
    const size = Math.min(this.width * widthShare, this.height * heightShare);
    const cx = this.width * centerX, cyy = this.height * centerY;
    return this.geometry.points.map((p) => {
      const x = p.x * cy - p.z * sy, z0 = p.x * sy + p.z * cy;
      const y = p.y * ct - z0 * st, z = p.y * st + z0 * ct;
      const scale = (size * 6) / (z + 6);
      return [cx + x * scale, cyy - y * scale, z];
    });
  }

  private dot(x: number, y: number, radius: number, fill: string) {
    this.ctx.fillStyle = fill;
    this.ctx.beginPath();
    this.ctx.arc(x, y, radius, 0, 7);
    this.ctx.fill();
  }

  private drawEdges(P: Projected[]) {
    const buckets: [number, number][][] = [[], [], [], []];
    for (const e of this.geometry.edges) {
      const depth = near((P[e[0]]![2] + P[e[1]]![2]) / 2);
      buckets[Math.min(3, Math.floor(depth * 4))]!.push(e);
    }
    this.ctx.lineWidth = 0.8;
    buckets.forEach((list, level) => {
      this.ctx.strokeStyle = toRgba(this.accent, 0.05 + level * 0.075);
      this.ctx.beginPath();
      for (const [a, c] of list) {
        this.ctx.moveTo(P[a]![0], P[a]![1]);
        this.ctx.lineTo(P[c]![0], P[c]![1]);
      }
      this.ctx.stroke();
    });
  }

  private drawPoints(P: Projected[]) {
    P.forEach((q, i) => {
      const depth = near(q[2]), f = this.flash[i]!, lit = f > 0.02;
      if (lit) this.dot(q[0], q[1], 3 + 12 * f, toRgba(this.accent, 0.32 * f));
      const fill = lit ? toRgba(this.accent, 0.6 + 0.4 * f) : `rgba(232,238,241,${0.14 + depth * 0.62})`;
      this.dot(q[0], q[1], 0.7 + depth * 1.6 + f * 1.4, fill);
    });
  }

  private drawPulses(P: Projected[]) {
    for (const p of this.pulses) {
      const a = P[p.from]!, b = P[p.to]!;
      const x = a[0] + (b[0] - a[0]) * p.t, y = a[1] + (b[1] - a[1]) * p.t;
      this.dot(x, y, 5, toRgba(this.accent, 0.3));
      this.dot(x, y, 2.2, toRgba(this.accent, 1));
    }
  }

  private fire(from: number) {
    const options = this.geometry.neighbors[from]!;
    if (options.length && this.pulses.length < MAX_PULSES) {
      this.pulses.push({ from, to: options[Math.floor(this.rand() * options.length)]!, t: 0 });
    }
  }

  private advancePulses(dt: number) {
    for (let i = this.pulses.length - 1; i >= 0; i--) {
      const p = this.pulses[i]!;
      p.t += dt / TRAVEL_MS;
      if (p.t < 1) continue;
      this.pulses.splice(i, 1);
      this.flash[p.to] = 1;
      if (this.rand() < 0.62) this.fire(p.to);
      if (this.rand() < 0.12) this.fire(p.to);
    }
  }
}

export function createBrainRenderer(canvas: HTMLCanvasElement, geometry: BrainGeometry, placement: BrainPlacement) {
  const drawing = new BrainDrawing(canvas, geometry, placement);
  return {
    resize: () => drawing.resize(),
    setColor: (hex: string) => drawing.setColor(hex),
    drawFrame: (deltaMs: number) => drawing.step(deltaMs),
    drawStill: () => drawing.draw(),
  };
}
