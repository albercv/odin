import { useState } from 'react';
import { api, intake, type S } from '../api/client';
import { IntakeCandidates, type Step } from '../components/IntakeCandidates';
import { IntakeGroups } from '../components/IntakeGroups';
import { IntakeSummary } from '../components/IntakeSummary';
import { IntakeUnplaced } from '../components/IntakeUnplaced';
import { Notice } from '../components/Notice';
import { useProjectTerms, type ProjectTerms } from '../components/projectTerms';
import { useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { PageHeader } from '../ui/PageHeader';
import styles from '../components/Intake.module.css';

const steps = (t: ProjectTerms) => [`1. Lista de ${t.many}`, `2. ${t.Many}`, '3. Parejas', '4. Fichas y expedientes'];
const NONE: S['ProjectView'][] = [];

const batchInUrl = () => new URLSearchParams(location.search).get('lote') ?? undefined;

function StepBody({ step, ...props }: Step & { step: number }) {
  const t = useProjectTerms();
  if (step === 0) return <IntakeCandidates {...props} />;
  if (step === 1) {
    return (
      <IntakeGroups {...props} kinds={['PROJECT', 'COMPANY_WIDE']} title={t.Many} empty={`No hay propuestas de ${t.one} por decidir`}
        description="Cada grupo se despliega para ver sus documentos y los motivos antes de confirmarlo. Nada cambia sin tu confirmación.">
        <IntakeUnplaced {...props} />
      </IntakeGroups>
    );
  }
  if (step === 2) {
    return <IntakeGroups {...props} kinds={['SAME_AS_SCAN', 'SUPERSEDES']} title="Parejas" empty="No hay parejas por decidir"
      description={`Escaneos y su copia, y versiones que sustituyen a otras. Solo se confirman cuando ${t.these} ${t.many} de sus documentos ya están ${t.agree('confirmados', 'confirmadas')}.`} />;
  }
  return <IntakeGroups {...props} kinds={['CASE_MARK', 'CASE_LINK']} title="Fichas y expedientes" empty="No hay fichas ni expedientes por decidir"
    description={`Documentos de caso y enlaces a las fichas de caso. Solo se confirman cuando sus ${t.many} ya están ${t.agree('confirmados', 'confirmadas')}.`} />;
}

function Batch({ companyId, batchId }: { companyId: string; batchId: string }) {
  const batch = useLoad(() => intake.batch(companyId, batchId), [companyId, batchId]);
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const [step, setStep] = useState(0);
  const t = useProjectTerms();
  const changed = () => { batch.reload(); projects.reload(); };
  return (
    <>
      <Notice message={batch.error ?? projects.error} />
      {batch.data && <IntakeSummary batch={batch.data} />}
      <nav className={styles.steps} aria-label="Pasos de la primera carga">
        {steps(t).map((text, i) => <Button key={text} variant={i === step ? 'primary' : 'secondary'} aria-current={i === step ? 'step' : undefined} onClick={() => setStep(i)}>{text}</Button>)}
      </nav>
      <StepBody step={step} companyId={companyId} batchId={batchId} projects={projects.data ?? NONE} onChanged={changed} batch={batch.data} />
    </>
  );
}

const DESCRIPTION = 'Confirma cómo se ordena el archivo de un cliente nuevo. Nadie ve un documento hasta que lo confirmas.';

/** `?lote=<id>` is a deep link to one batch; otherwise the most recent batch not closed. The panel never creates batches: the command does. */
export function Intake({ companyId }: { companyId: string }) {
  const linked = batchInUrl();
  const open = useLoad(() => (linked ? Promise.resolve(undefined) : intake.open(companyId)), [companyId, linked]);
  const batchId = linked ?? open.data?.batches[0]?.id;
  return (
    <div>
      <PageHeader title="Primera carga" description={DESCRIPTION} />
      <Notice message={open.error} />
      {batchId && <Batch key={batchId} companyId={companyId} batchId={batchId} />}
      {!linked && open.data?.batches.length === 0 && <EmptyState title="No hay ninguna primera carga en curso: se empieza con la orden de la guía" />}
    </div>
  );
}
