import { useState } from 'react';
import { intake, type S } from '../api/client';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { Section } from '../ui/Section';
import { Table, Td, Tr } from '../ui/Table';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './Intake.module.css';

export type Step = { companyId: string; batchId: string; projects: S['ProjectView'][]; onChanged: () => void };
type Decide = (decision: S['ProjectDecisionRequest']) => void;

const ORIGIN: Record<string, string> = { IMPORTED: 'De la lista del cliente', DISCOVERED: 'De los textos' };
const STATE: Record<string, string> = { PROPOSED: 'Por decidir', CONFIRMED: 'Confirmado', REJECTED: 'Rechazado' };

function CandidateActions({ candidate, projects, decide }: { candidate: S['ProjectCandidateView']; projects: S['ProjectView'][]; decide: Decide }) {
  const [projectId, setProjectId] = useState('');
  const { name } = candidate;
  return (
    <div className={styles.tools}>
      <Button variant="primary" aria-label={`Crear ${name}`} onClick={() => decide({ name, decision: 'CREATE' })}>Crear</Button>
      <select aria-label={`Unir ${name} a`} value={projectId} onChange={(e) => setProjectId(e.target.value)}>
        <option value="">Un proyecto…</option>
        {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
      </select>
      <Button aria-label={`Unir ${name}`} disabled={!projectId} onClick={() => decide({ name, decision: 'MERGE', projectId })}>Unir</Button>
      <Button variant="ghost" aria-label={`Rechazar ${name}`} onClick={() => decide({ name, decision: 'REJECT' })}>Rechazar</Button>
    </div>
  );
}

/** Step 1: the list of projects (D6). Without a confirmed list no project is proposed. */
export function IntakeCandidates({ companyId, batchId, projects, onChanged }: Step) {
  const list = useLoad(() => intake.candidates(companyId, batchId), [companyId, batchId]);
  const { error, act } = useAction();
  const run = async (fn: () => Promise<unknown>) => { if (await act(fn)) { list.reload(); onChanged(); } };
  const decide: Decide = (d) => run(() => intake.confirmCandidates(companyId, batchId, [d]));
  const candidates = list.data?.candidates ?? [];
  return (
    <Section title="Lista de proyectos" description="Importa la lista del programa de gestión o busca los proyectos en los textos. Sin una lista confirmada no se proponen proyectos."
      actions={<Button onClick={() => run(() => intake.discover(companyId, batchId))}>Buscar proyectos en los textos</Button>}>
      <div className={styles.body}>
        <label>Importar la lista de proyectos <input type="file" accept=".csv,.txt" onChange={(e) => { const file = e.target.files?.[0]; if (file) run(() => intake.importList(companyId, batchId, file)); }} /></label>
        <Notice message={list.error ?? error} />
      </div>
      {candidates.length === 0 && !list.error && <EmptyState title="Todavía no hay candidatos" />}
      {candidates.length > 0 && (
        <Table columns={['Proyecto', 'Origen', 'Documentos', 'Estado']}>
          {candidates.map((c) => (
            <Tr key={c.name}>
              <Td label="Proyecto">{c.name}</Td><Td label="Origen">{ORIGIN[c.origin] ?? c.origin}</Td>
              <Td label="Documentos">{c.documentCount}</Td><Td label="Estado">{STATE[c.state] ?? c.state}</Td>
              <Td>{c.state === 'PROPOSED' && <CandidateActions candidate={c} projects={projects} decide={decide} />}</Td>
            </Tr>
          ))}
        </Table>
      )}
    </Section>
  );
}
