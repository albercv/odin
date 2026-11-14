import { useState } from 'react';
import { api, intake, type S } from '../api/client';
import { IntakeCandidates, type Step } from '../components/IntakeCandidates';
import { IntakeGroups } from '../components/IntakeGroups';
import { IntakeStart } from '../components/IntakeStart';
import { IntakeSummary } from '../components/IntakeSummary';
import { IntakeUnplaced } from '../components/IntakeUnplaced';
import { Notice } from '../components/Notice';
import { useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { PageHeader } from '../ui/PageHeader';
import styles from '../components/Intake.module.css';

const STEPS = ['1. Lista de proyectos', '2. Proyectos', '3. Parejas', '4. Fichas y expedientes'];
const NONE: S['ProjectView'][] = [];

const batchInUrl = () => new URLSearchParams(location.search).get('lote') ?? undefined;
const rememberBatch = (id: string) => history.replaceState(null, '', `${location.pathname}?lote=${id}`);

function StepBody({ step, ...props }: Step & { step: number }) {
  if (step === 0) return <IntakeCandidates {...props} />;
  if (step === 1) {
    return (
      <IntakeGroups {...props} kinds={['PROJECT', 'COMPANY_WIDE']} title="Proyectos" empty="No hay propuestas de proyecto por decidir"
        description="Cada grupo se despliega para ver sus documentos y los motivos antes de confirmarlo. Nada cambia sin tu confirmación.">
        <IntakeUnplaced {...props} />
      </IntakeGroups>
    );
  }
  if (step === 2) {
    return <IntakeGroups {...props} kinds={['SAME_AS_SCAN', 'SUPERSEDES']} title="Parejas" empty="No hay parejas por decidir"
      description="Escaneos y su copia, y versiones que sustituyen a otras. Solo se confirman cuando los proyectos de sus documentos ya están confirmados." />;
  }
  return <IntakeGroups {...props} kinds={['CASE_MARK', 'CASE_LINK']} title="Fichas y expedientes" empty="No hay fichas ni expedientes por decidir"
    description="Documentos de caso y enlaces a las fichas de caso. Solo se confirman cuando sus proyectos ya están confirmados." />;
}

function Batch({ companyId, batchId }: { companyId: string; batchId: string }) {
  const batch = useLoad(() => intake.batch(companyId, batchId), [companyId, batchId]);
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const [step, setStep] = useState(0);
  const changed = () => { batch.reload(); projects.reload(); };
  return (
    <>
      <Notice message={batch.error ?? projects.error} />
      {batch.data && <IntakeSummary batch={batch.data} />}
      <nav className={styles.steps} aria-label="Pasos de la primera carga">
        {STEPS.map((text, i) => <Button key={text} variant={i === step ? 'primary' : 'secondary'} aria-current={i === step ? 'step' : undefined} onClick={() => setStep(i)}>{text}</Button>)}
      </nav>
      <StepBody step={step} companyId={companyId} batchId={batchId} projects={projects.data ?? NONE} onChanged={changed} />
    </>
  );
}

const DESCRIPTION = 'Confirma cómo se ordena el archivo de un cliente nuevo. Nadie ve un documento hasta que lo confirmas.';

/** The batch comes from `?lote=` (the command's link) or from the batch this page opens. */
export function Intake({ companyId }: { companyId: string }) {
  const [batchId, setBatchId] = useState(batchInUrl);
  const started = (batch: S['IntakeBatchView']) => { rememberBatch(batch.id); setBatchId(batch.id); };
  return (
    <div>
      <PageHeader title="Primera carga" description={DESCRIPTION} />
      {batchId ? <Batch key={batchId} companyId={companyId} batchId={batchId} /> : <IntakeStart companyId={companyId} onStarted={started} />}
    </div>
  );
}
