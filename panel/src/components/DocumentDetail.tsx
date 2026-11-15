import { useState } from 'react';
import { api, originalUrl, type S } from '../api/client';
import { STATUS } from '../pages/documentStatus';
import { formatDate, projectName } from '../pages/reviewNames';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { FormDialog } from '../ui/FormDialog';
import { StatusPill } from '../ui/StatusPill';
import { Notice } from './Notice';
import { useAction } from './useLoad';
import { DocumentTopics } from './DocumentTopics';
import { useProjectTerms } from './projectTerms';
import styles from './DocumentDetail.module.css';

type Props = { document: S['DocumentView']; projects: S['ProjectView'][]; onClose: () => void; onChanged?: () => void };

const UNLINK_MESSAGE = 'Deshacer el enlace. El escaneo volverá a salir como un documento más en las listas y las búsquedas.';

/** Los escaneos no salen en la lista: la ficha de uno se abre por su id. */
function ScanCopies({ copies, open }: { copies: S['ScanLink'][]; open: (id: string) => void }) {
  if (copies.length === 0) return null;
  return (
    <section>
      <h3>Copias escaneadas</h3>
      <ul>
        {copies.map((c) => (
          <li key={c.documentId}>
            <a href={`#${c.documentId}`} onClick={(e) => { e.preventDefault(); open(c.documentId); }}>{c.title}</a>{' '}
            <a href={originalUrl(c.documentId)} download aria-label={`Ver el original de ${c.title}`}>Ver el original</a>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ScanOf({ of, open, onUnlink }: { of: S['ScanLink']; open: (id: string) => void; onUnlink: () => void }) {
  return (
    <p>
      {'Copia escaneada de '}
      <a href={`#${of.documentId}`} onClick={(e) => { e.preventDefault(); open(of.documentId); }}>{of.title}</a>{' '}
      <Button onClick={onUnlink}>No es una copia escaneada</Button>
    </p>
  );
}

function Facts({ d, projects }: { d: S['DocumentView']; projects: S['ProjectView'][] }) {
  const [label, tone] = STATUS[d.status] ?? [d.status, 'off' as const];
  const t = useProjectTerms();
  return (
    <dl className={styles.facts}>
        <div><dt>Estado</dt><dd><StatusPill tone={tone}>{label}</StatusPill></dd></div>
        <div><dt>{t.One}</dt><dd>{projectName(d.projectId, projects, t.noneOne)}</dd></div>
        <div><dt>Fecha de alta</dt><dd>{formatDate(d.uploadedAt)}</dd></div>
        {d.supersededBy && (
          <div>
            <dt>Sustitución</dt>
            <dd>{`Sustituido por ${d.supersededBy.title}`}</dd>
            {d.supersededAt && <dd>{`Marcado el ${formatDate(d.supersededAt)}`}</dd>}
          </div>
        )}
    </dl>
  );
}

export function DocumentDetail({ document, projects, onClose, onChanged }: Props) {
  const [d, setD] = useState(document);
  const confirm = useConfirm();
  const { error, act } = useAction();
  const open = async (id: string) => { await act(async () => setD(await api.document(id))); };
  const unlink = async () => {
    if (await confirm({ message: UNLINK_MESSAGE, confirmLabel: 'Deshacer el enlace', danger: true }) && await act(() => api.unlinkScanCopy(d.id))) {
      onChanged?.();
      setD(await api.document(d.id));
    }
  };
  return (
    <FormDialog title={d.title} open onClose={onClose}>
      <Notice message={error} />
      <Facts d={d} projects={projects} />
      {d.scanOf && <ScanOf of={d.scanOf} open={open} onUnlink={unlink} />}
      <ScanCopies copies={d.scanCopies ?? []} open={open} />
      <p><a href={originalUrl(d.id)} download>Descargar original</a></p>
      <DocumentTopics documentId={d.id} companyId={d.companyId} />
    </FormDialog>
  );
}
