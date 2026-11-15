import { useState } from 'react';
import { intake, type S } from '../api/client';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { Section } from '../ui/Section';
import { Table, Td, Tr } from '../ui/Table';
import { Notice } from './Notice';
import { useProjectTerms } from './projectTerms';
import { useAction, useLoad } from './useLoad';
import styles from './Intake.module.css';

export type Step = { companyId: string; batchId: string; projects: S['ProjectView'][]; onChanged: () => void; batch?: S['IntakeBatchView'] };
type Decide = (decision: S['ProjectDecisionRequest']) => void;

const ORIGIN: Record<string, string> = { IMPORTED: 'De la lista del cliente', DISCOVERED: 'De los textos' };
const STATE: Record<string, string> = { PROPOSED: 'Por decidir', CONFIRMED: 'Confirmado', REJECTED: 'Rechazado' };

function CandidateActions({ candidate, projects, decide }: { candidate: S['ProjectCandidateView']; projects: S['ProjectView'][]; decide: Decide }) {
  const [projectId, setProjectId] = useState('');
  const t = useProjectTerms();
  const { name } = candidate;
  return (
    <div className={styles.tools}>
      <Button variant="primary" aria-label={`Crear ${name}`} onClick={() => decide({ name, decision: 'CREATE' })}>Crear</Button>
      <select aria-label={`Unir ${name} a`} value={projectId} onChange={(e) => setProjectId(e.target.value)}>
        <option value="">{t.AOne.split(' ')[0]} {t.one}…</option>
        {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
      </select>
      <Button aria-label={`Unir ${name}`} disabled={!projectId} onClick={() => decide({ name, decision: 'MERGE', projectId })}>Unir</Button>
      <Button variant="ghost" aria-label={`Rechazar ${name}`} onClick={() => decide({ name, decision: 'REJECT' })}>Rechazar</Button>
    </div>
  );
}

function CandidateTable({ candidates, projects, decide }: { candidates: S['ProjectCandidateView'][]; projects: S['ProjectView'][]; decide?: Decide }) {
  const t = useProjectTerms();
  return (
    <Table columns={[t.One, 'Origen', 'Documentos', 'Estado']}>
      {candidates.map((c) => (
        <Tr key={c.name}>
          <Td label={t.One}>{c.name}</Td><Td label="Origen">{ORIGIN[c.origin] ?? c.origin}</Td>
          <Td label="Documentos">{c.documentCount}</Td><Td label="Estado">{STATE[c.state] ?? c.state}</Td>
          <Td>{c.state === 'PROPOSED' && decide && <CandidateActions candidate={c} projects={projects} decide={decide} />}</Td>
        </Tr>
      ))}
    </Table>
  );
}

const reading = (n: number) => (n === 1 ? 'Aún se está leyendo 1 documento' : `Aún se están leyendo ${n} documentos`);

/** «Lista terminada»: only with every candidate decided and nothing left to read; the API checks it again (409). */
function FinishList({ batch, undecided, finish }: { batch?: S['IntakeBatchView']; undecided: number; finish: () => void }) {
  if (!batch || batch.state !== 'READING') return null;
  const missing = undecided > 0 ? `Faltan ${undecided} por decidir` : batch.reading > 0 ? reading(batch.reading) : undefined;
  return (
    <div className={styles.tools}>
      <Button variant="primary" disabled={missing !== undefined} onClick={finish}>Lista terminada: clasificar</Button>
      {missing && <p>{missing}</p>}
    </div>
  );
}

/** Step 1: the list of projects (D6). Nothing is classified until the admin says the list is finished (spec 4.4). */
export function IntakeCandidates({ companyId, batchId, projects, onChanged, batch }: Step) {
  const list = useLoad(() => intake.candidates(companyId, batchId), [companyId, batchId]);
  const t = useProjectTerms();
  const { error, act } = useAction();
  const run = async (fn: () => Promise<unknown>) => { if (await act(fn)) { list.reload(); onChanged(); } };
  const decide: Decide = (d) => run(() => intake.confirmCandidates(companyId, batchId, [d]));
  // a 409 may come with candidates just found in the texts: reload the list either way
  const finish = async () => { await act(() => intake.closeList(companyId, batchId)); list.reload(); onChanged(); };
  const candidates = list.data?.candidates ?? [];
  const closed = !!batch && (!!batch.projectListClosedAt || batch.state === 'IN_REVIEW' || batch.state === 'CLOSED');
  const undecided = candidates.filter((c) => c.state === 'PROPOSED').length;
  return (
    <Section title={`Lista de ${t.many}`}
      description={`Importa la lista del programa de gestión o busca ${t.these} ${t.many} en los textos. Cuando no quede ${t.agree('ninguno', 'ninguna')} por decidir, da la lista por terminada: hasta entonces no se proponen ${t.many}.`}
      actions={<Button disabled={closed} onClick={() => run(() => intake.discover(companyId, batchId))}>Buscar {t.many} en los textos</Button>}>
      <div className={styles.body}>
        {closed ? <p>{batch?.state === 'READING' ? 'Lista terminada: Odín está clasificando los documentos.' : 'Lista terminada.'}</p>
          : <label>Importar la lista de {t.many} <input type="file" accept=".csv,.txt" onChange={(e) => { const file = e.target.files?.[0]; if (file) run(() => intake.importList(companyId, batchId, file)); }} /></label>}
        {!closed && list.data && <FinishList batch={batch} undecided={undecided} finish={finish} />}
        <Notice message={list.error ?? error} />
      </div>
      {candidates.length === 0 && !list.error && <EmptyState title="Todavía no hay candidatos" />}
      {candidates.length > 0 && <CandidateTable candidates={candidates} projects={projects} decide={closed ? undefined : decide} />}
    </Section>
  );
}
