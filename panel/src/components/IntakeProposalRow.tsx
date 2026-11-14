import { useState } from 'react';
import type { S } from '../api/client';
import { Button } from '../ui/Button';
import styles from './Intake.module.css';

export type Decision = S['DecideProposalRequest'];
type Props = { kind: string; item: S['IntakeProposalItem']; projectKind: boolean; projects: S['ProjectView'][]; decide: (id: string, d: Decision) => void; original: string };

function CorrectProject({ title, projects, onCorrect }: { title: string; projects: S['ProjectView'][]; onCorrect: (projectId: string) => void }) {
  const [projectId, setProjectId] = useState('');
  return (
    <>
      <select aria-label={`Proyecto correcto de ${title}`} value={projectId} onChange={(e) => setProjectId(e.target.value)}>
        <option value="">Otro proyecto…</option>
        {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
      </select>
      <Button aria-label={`Corregir proyecto de ${title}`} disabled={!projectId} onClick={() => onCorrect(projectId)}>Corregir</Button>
    </>
  );
}

const RELATION: Record<string, string> = { SAME_AS_SCAN: 'es la copia escaneada de', SUPERSEDES: 'sustituye a', CASE_LINK: 'se añade a la ficha' };

/** «X sustituye a Y»: the other document comes with its title from the API. */
const relation = (kind: string, item: S['IntakeProposalItem']) =>
  item.targetTitle && RELATION[kind] ? `${item.title} ${RELATION[kind]} ${item.targetTitle}` : undefined;

/** One proposal with its literal reason; the projectKind ones can also go company-wide, to another project or stay hidden. */
export function IntakeProposalRow({ kind, item, projectKind, projects, decide, original }: Props) {
  const { id, title } = item;
  const button = (text: string, decision: Decision, variant: 'primary' | 'secondary' | 'ghost' = 'secondary') =>
    <Button variant={variant} aria-label={`${text} ${title}`} onClick={() => decide(id, decision)}>{text}</Button>;
  return (
    <li className={styles.row}>
      <p><strong>{title}</strong> <a href={original} target="_blank" rel="noreferrer" aria-label={`Ver el original de ${title}`}>Ver el original</a></p>
      {relation(kind, item) && <p>{relation(kind, item)}</p>}
      {item.caseNumber && <p>{`Caso ${item.caseNumber}`}</p>}
      <p className={styles.reason}>{item.reason}</p>
      <div className={styles.tools}>
        {button('Confirmar', { decision: 'CONFIRM' }, 'primary')}
        {projectKind && button('De toda la empresa', { decision: 'COMPANY_WIDE' })}
        {projectKind && button('No lo sé', { decision: 'DONT_KNOW' })}
        {button('Rechazar', { decision: 'REJECT' }, 'ghost')}
        {projectKind && <CorrectProject title={title} projects={projects} onCorrect={(projectId) => decide(id, { decision: 'CORRECT_PROJECT', projectId })} />}
      </div>
    </li>
  );
}
