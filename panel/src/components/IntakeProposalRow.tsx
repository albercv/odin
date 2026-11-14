import { useState } from 'react';
import type { S } from '../api/client';
import { Button } from '../ui/Button';
import styles from './Intake.module.css';

export type Decision = S['DecideProposalRequest'];
type Props = { item: S['IntakeProposalItem']; projectKind: boolean; projects: S['ProjectView'][]; decide: (id: string, d: Decision) => void };

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

/** One proposal with its literal reason; the projectKind ones can also go company-wide, to another project or stay hidden. */
export function IntakeProposalRow({ item, projectKind, projects, decide }: Props) {
  const { id, title } = item;
  const button = (text: string, decision: Decision, variant: 'primary' | 'secondary' | 'ghost' = 'secondary') =>
    <Button variant={variant} aria-label={`${text} ${title}`} onClick={() => decide(id, decision)}>{text}</Button>;
  return (
    <li className={styles.row}>
      <p><strong>{title}</strong></p>
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
