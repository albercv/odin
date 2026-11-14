import { useState } from 'react';
import { intake, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Section } from '../ui/Section';
import type { Step } from './IntakeCandidates';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './Intake.module.css';

type Place = S['PlaceDocumentRequest'];

function UnplacedRow({ doc, projects, place, discard }: { doc: S['IntakeDocumentView']; projects: S['ProjectView'][]; place: (p: Place) => void; discard: () => void }) {
  const [projectId, setProjectId] = useState('');
  const { title } = doc;
  return (
    <li className={styles.row}>
      <p><strong>{title}</strong></p>
      {doc.path && <p className={styles.reason}>{doc.path}</p>}
      {doc.status === 'FAILED' && <p className={styles.reason}>No se pudo leer este documento.</p>}
      <div className={styles.tools}>
        <select aria-label={`Proyecto de ${title}`} value={projectId} onChange={(e) => setProjectId(e.target.value)}>
          <option value="">Un proyecto…</option>
          {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <Button variant="primary" aria-label={`Colocar ${title}`} disabled={!projectId} onClick={() => place({ projectId })}>Colocar</Button>
        <Button aria-label={`De toda la empresa ${title}`} onClick={() => place({ companyWide: true })}>De toda la empresa</Button>
        <Button variant="danger" aria-label={`Descartar ${title}`} onClick={discard}>Descartar</Button>
      </div>
    </li>
  );
}

/** Documents with no proposal: the admin places them or discards them. */
export function IntakeUnplaced({ companyId, batchId, projects, onChanged }: Step) {
  const list = useLoad(() => intake.unplaced(companyId, batchId), [companyId, batchId]);
  const { error, act } = useAction();
  const confirm = useConfirm();
  const run = async (fn: () => Promise<unknown>) => { if (await act(fn)) { list.reload(); onChanged(); } };
  const discard = async (doc: S['IntakeDocumentView']) => {
    if (await confirm({ message: `¿Descartar ${doc.title}? Dejará de aparecer en esta carga.`, confirmLabel: 'Descartar', danger: true })) run(() => intake.withdraw(companyId, batchId, doc.id));
  };
  const docs = list.data?.documents ?? [];
  return (
    <Section title="Sin ubicar" description="Documentos sin propuesta de proyecto. Colócalos en un proyecto, deja que los vea toda la empresa o descártalos.">
      <div className={styles.body}>
        <Notice message={list.error ?? error} />
        {list.data && docs.length === 0 && <p>No queda ningún documento sin ubicar.</p>}
        <ul className={styles.items}>
          {docs.map((d) => <UnplacedRow key={d.id} doc={d} projects={projects} place={(p) => run(() => intake.place(companyId, batchId, d.id, p))} discard={() => discard(d)} />)}
        </ul>
      </div>
    </Section>
  );
}
