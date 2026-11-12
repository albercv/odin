import { StatusPill } from '../ui/StatusPill';

const LABELS: Record<string, [string, 'ok' | 'off' | 'pending']> = {
  TAGGING: ['Etiquetando', 'pending'],
  DONE: ['Etiquetado', 'ok'],
  FAILED: ['Falló', 'off'],
};

/** No row yet (or PENDING) reads «Sin etiquetar». */
export function TaggingStatus({ status }: { status?: string }) {
  const [label, tone] = LABELS[status ?? ''] ?? ['Sin etiquetar', 'off'];
  return <StatusPill tone={tone}>{label}</StatusPill>;
}
