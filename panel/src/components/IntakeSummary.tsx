import type { S } from '../api/client';
import { Section } from '../ui/Section';
import { useProjectTerms, type ProjectTerms } from './projectTerms';
import styles from './Intake.module.css';

const proposed = (t: ProjectTerms): Record<string, string> => ({
  PROJECT: `${t.Many} ${t.agree('propuestos', 'propuestas')}`, COMPANY_WIDE: 'De toda la empresa propuestos', SAME_AS_SCAN: 'Escaneos propuestos',
  SUPERSEDES: 'Versiones propuestas', CASE_MARK: 'Casos propuestos', CASE_LINK: 'Enlaces propuestos',
});

const pendingWarning = (n: number) => `${n === 1 ? '1 documento sigue pendiente y ningún trabajador lo ve' : `${n} documentos siguen pendientes y ningún trabajador los ve`}. «No lo sé» los deja ocultos, sin plazo.`;

function Warnings({ batch }: { batch: S['IntakeBatchView'] }) {
  const messages = [...batch.warnings.map((w) => w.message), ...(batch.pending > 0 ? [pendingWarning(batch.pending)] : [])];
  if (messages.length === 0) return null;
  return <section className={styles.warnings} aria-label="Avisos"><ul>{messages.map((m) => <li key={m}>{m}</li>)}</ul></section>;
}

function Count({ label, value }: { label: string; value: number }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}

/** Counts of the batch: never per person (D-005). */
export function IntakeSummary({ batch }: { batch: S['IntakeBatchView'] }) {
  const labels = proposed(useProjectTerms());
  return (
    <>
      <Warnings batch={batch} />
      <Section title="Recuentos">
        <div className={styles.body}>
          <dl className={styles.counts}>
            <Count label="Subidos" value={batch.uploaded} /><Count label="En lectura" value={batch.reading} />
            <Count label="Sin confirmar" value={batch.pending} /><Count label="Ya estaban" value={batch.alreadyThere} />
            <Count label="Descartados" value={batch.discarded} /><Count label="Sin ubicar" value={batch.unplaced} />
            {Object.entries(batch.proposed).map(([kind, n]) => <Count key={kind} label={labels[kind] ?? kind} value={n} />)}
          </dl>
        </div>
      </Section>
    </>
  );
}
