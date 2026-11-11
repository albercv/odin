export type FrameSummary = { averageMs: number; medianMs: number };

function summarize(samples: number[]): FrameSummary {
  const sorted = [...samples].sort((a, b) => a - b);
  const mid = sorted.length >> 1;
  const medianMs = sorted.length % 2 ? sorted[mid]! : (sorted[mid - 1]! + sorted[mid]!) / 2;
  return { averageMs: samples.reduce((sum, ms) => sum + ms, 0) / samples.length, medianMs };
}

export function createFrameGuard(budgetMs = 12, sampleSize = 60) {
  const samples: number[] = [];
  let decided: FrameSummary | undefined;
  return {
    record(frameMs: number): 'continue' | 'freeze' {
      if (decided) return 'continue';
      samples.push(frameMs);
      if (samples.length < sampleSize) return 'continue';
      decided = summarize(samples);
      return decided.averageMs > budgetMs ? 'freeze' : 'continue';
    },
    summary: () => decided,
  };
}
