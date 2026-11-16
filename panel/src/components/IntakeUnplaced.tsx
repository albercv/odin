import { useState } from 'react';
import { intake, intakeOriginalUrl, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Section } from '../ui/Section';
import type { Step } from './IntakeCandidates';
import { documentTypeLabel } from './documentType';
import { Notice } from './Notice';
import { useProjectTerms } from './projectTerms';
import { useAction, useLoad } from './useLoad';
import styles from './Intake.module.css';

type Place = S['PlaceDocumentRequest'];

const STATUS: Record<string, string> = { FAILED: 'No se pudo leer este documento.', PENDING_READER: 'Esperando al lector OCR.' };

type RowProps = { doc: S['IntakeDocumentView']; original: string; projects: S['ProjectView'][]; place: (p: Place) => void; discard: () => void };

function UnplacedRow({ doc, original, projects, place, discard }: RowProps) {
  const [projectId, setProjectId] = useState('');
  const t = useProjectTerms();
  const { title } = doc;
  return (
    <li className={styles.row}>
      <p><strong>{title}</strong> <a href={original} target="_blank" rel="noreferrer" aria-label={`Ver el original de ${title}`}>Ver el original</a></p>
      <p className={styles.reason}>{documentTypeLabel(doc.documentType)}</p>
      {doc.path && <p className={styles.reason}>{doc.path}</p>}
      {STATUS[doc.status] && <p className={styles.reason}>{STATUS[doc.status]}</p>}
      <div className={styles.tools}>
        <select aria-label={`${t.One} de ${title}`} value={projectId} onChange={(e) => setProjectId(e.target.value)}>
          <option value="">{t.AOne.split(' ')[0]} {t.one}…</option>
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
  const t = useProjectTerms();
  const run = async (fn: () => Promise<unknown>) => { if (await act(fn)) { list.reload(); onChanged(); } };
  const discard = async (doc: S['IntakeDocumentView']) => {
    if (await confirm({ message: `¿Descartar ${doc.title}? Dejará de aparecer en esta carga.`, confirmLabel: 'Descartar', danger: true })) run(() => intake.withdraw(companyId, batchId, doc.id));
  };
  const docs = list.data?.documents ?? [];
  return (
    <Section title="Sin ubicar" description={`Documentos sin propuesta de ${t.one}. Colócalos en ${t.aOne}, deja que los vea toda la empresa o descártalos.`}>
      <div className={styles.body}>
        <Notice message={list.error ?? error} />
        {list.data && docs.length === 0 && <p>No queda ningún documento sin ubicar.</p>}
        <ul className={styles.items}>
          {docs.map((d) => <UnplacedRow key={d.id} doc={d} original={intakeOriginalUrl(companyId, batchId, d.id)} projects={projects} place={(p) => run(() => intake.place(companyId, batchId, d.id, p))} discard={() => discard(d)} />)}
        </ul>
      </div>
    </Section>
  );
}
