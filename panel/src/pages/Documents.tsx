import { useState } from 'react';
import { MAX_DOCUMENTS, api, type S } from '../api/client';
import { AiMark } from '../components/AiMark';
import { DocumentDetail } from '../components/DocumentDetail';
import { Notice } from '../components/Notice';
import { SupersedeDialog } from '../components/SupersedeDialog';
import { TaggingStatus } from '../components/TaggingStatus';
import { UploadDialog } from '../components/UploadDialog';
import { useAction, useLoad } from '../components/useLoad';
import { useConfirm } from '../ui/ConfirmDialog';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { PageHeader } from '../ui/PageHeader';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, Td, Tr, tableStyles } from '../ui/Table';
import { NO_READER_LABEL, STATUS } from './documentStatus';
import styles from './Documents.module.css';

type RowHandlers = { onRetry: () => void; onDetail: () => void; onSupersede: () => void; onUnmark: () => void; onToggleCase: () => void; onWithdraw: () => void; onRestore: () => void };
type Selection = { selected: boolean; onSelect: () => void };

function DocActions({ d, onDetail, onSupersede, onUnmark, onToggleCase, onWithdraw }: { d: S['DocumentView'] } & RowHandlers) {
  return (
    <RowActions label={`Más acciones de ${d.title}`}>
      <RowAction variant="ghost" text="Ver detalle" target={d.title} label="Ver detalle de" onClick={onDetail} />
      {d.caseDocument
        ? <RowAction variant="ghost" text="Quitar marca de caso" label="Quitar marca de caso de" target={d.title} onClick={onToggleCase} />
        : <RowAction variant="ghost" text="Marcar como caso" target={d.title} onClick={onToggleCase} />}
      <RowAction variant="ghost" text="Marcar como sustituido…" label="Marcar como sustituido" target={d.title} onClick={onSupersede} />
      {d.supersededBy && <RowAction variant="ghost" text="Quitar marca de sustituido" label="Quitar marca de sustituido de" target={d.title} onClick={onUnmark} />}
      <RowAction variant="ghost" text="Retirar" target={d.title} onClick={onWithdraw} />
    </RowActions>
  );
}

function RowEnd({ d, ...handlers }: { d: S['DocumentView'] } & RowHandlers) {
  if (d.withdrawnAt) return <RowAction text="Recuperar" target={d.title} onClick={handlers.onRestore} />;
  return (
    <>
      {d.status === 'FAILED' && <RowAction text="Reintentar" target={d.title} onClick={handlers.onRetry} />}
      <DocActions d={d} {...handlers} />
    </>
  );
}

function DocRow({ d, scannedDocumentsSupported, selected, onSelect, ...handlers }: { d: S['DocumentView']; scannedDocumentsSupported: boolean } & Selection & RowHandlers) {
  const [known, tone] = STATUS[d.status] ?? [d.status, 'off' as const];
  const label = d.status === 'PENDING_READER' && !scannedDocumentsSupported ? NO_READER_LABEL : known;
  return (
    <Tr>
      <Td label="Título">
        {!d.withdrawnAt && <input type="checkbox" className={styles.select} aria-label={`Seleccionar ${d.title}`} checked={selected} onChange={onSelect} />}
        {d.title}
        {d.caseDocument && <> <StatusPill tone="ok">Caso</StatusPill> {!d.withdrawnAt && <a href={`/casos/${d.id}?empresa=${d.companyId}`} aria-label={`Ver expediente de ${d.title}`}>Ver expediente</a>}</>}
        {d.supersededBy && <div className={styles.superseded}><StatusPill tone="off">Sustituido</StatusPill><span>{`por ${d.supersededBy.title}`}</span></div>}
      </Td>
      <Td label="Estado">
        <StatusPill tone={tone}>{label}</StatusPill>
        {d.pagesWithoutText.length > 0 && <div className={styles.superseded}>{`Páginas sin texto, no leídas: ${d.pagesWithoutText.join(', ')}`}</div>}
      </Td>
      <Td label="Temas">
        {d.topics.map((t) => <div key={t.id} className={styles.topic}>{t.name}{t.source === 'AI' && <AiMark />}</div>)}
        <TaggingStatus status={d.taggingStatus} />
      </Td>
      <Td label="Subido por">{d.uploadedByName}</Td>
      <Td label="Motivo">{d.failureReason}</Td>
      <Td className={tableStyles.actions}><RowEnd d={d} {...handlers} /></Td>
    </Tr>
  );
}

type Open = { kind: 'detail' | 'supersede'; document: S['DocumentView'] };

function DocumentDialogs({ open, documents, projects, onClose, onDone }: { open?: Open; documents: S['DocumentView'][]; projects: S['ProjectView'][]; onClose: () => void; onDone: () => void }) {
  if (open?.kind === 'supersede') return <SupersedeDialog document={open.document} documents={documents} projects={projects} onClose={onClose} onDone={onDone} />;
  if (open?.kind === 'detail') return <DocumentDetail document={open.document} projects={projects} onClose={onClose} />;
  return null;
}

function HeaderActions({ onRefresh, onUpload, withdrawn, onWithdrawn }: { onRefresh: () => void; onUpload: () => void; withdrawn: boolean; onWithdrawn: () => void }) {
  return (
    <div className={styles.actions}>
      <Button aria-pressed={withdrawn} onClick={onWithdrawn}>Retirados</Button>
      <Button onClick={onRefresh}>Actualizar</Button>
      <Button variant="primary" icon="upload" onClick={onUpload}>Subir documento</Button>
    </div>
  );
}

function SelectionBar({ count, onMark }: { count: number; onMark: (caseDocument: boolean) => void }) {
  if (count === 0) return null;
  return (
    <div role="group" aria-label="Documentos seleccionados" className={styles.selection}>
      <span>{count === 1 ? '1 seleccionado' : `${count} seleccionados`}</span>
      <Button onClick={() => onMark(true)}>Marcar como caso</Button>
      <Button onClick={() => onMark(false)}>Quitar marca de caso</Button>
    </div>
  );
}

function useSelection(markSelected: (ids: string[], caseDocument: boolean) => Promise<unknown>) {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const toggle = (id: string) => setSelected((previous) => {
    const next = new Set(previous);
    if (!next.delete(id)) next.add(id);
    return next;
  });
  const mark = async (caseDocument: boolean) => {
    if (await markSelected([...selected], caseDocument)) setSelected(new Set());
  };
  return { selected, toggle, mark };
}

function useWithdraw(reloadAfter: (fn: () => Promise<unknown>) => Promise<unknown>) {
  const confirm = useConfirm();
  return async (d: S['DocumentView']) => {
    const message = `¿Retirar «${d.title}»? Dejará de salir en las búsquedas y en los expedientes, pero Odín guarda el original. Puedes recuperarlo desde «Retirados».`;
    if (await confirm({ message, confirmLabel: 'Retirar', danger: true })) await reloadAfter(() => api.withdraw(d.id));
  };
}

function Empty({ withdrawn }: { withdrawn: boolean }) {
  return withdrawn
    ? <EmptyState title="No hay documentos retirados" description="Los que retires aparecerán aquí para recuperarlos." />
    : <EmptyState title="Aún no hay documentos" description="Sube el primero con «Subir documento»." />;
}

export function Documents({ companyId, scannedDocumentsSupported = false }: { companyId: string; scannedDocumentsSupported?: boolean }) {
  const [withdrawn, setWithdrawn] = useState(false);
  const list = useLoad(() => api.documents(companyId, withdrawn), [companyId, withdrawn]);
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const [uploading, setUploading] = useState(false);
  const [open, setOpen] = useState<Open>();
  const { error, act } = useAction();
  const reloadAfter = async (fn: () => Promise<unknown>) => (await act(fn)) && (list.reload(), true);
  const { selected, toggle, mark } = useSelection((ids, caseDocument) => reloadAfter(() => api.markCases(companyId, ids, caseDocument)));
  const withdraw = useWithdraw(reloadAfter);
  return (
    <div>
      <PageHeader title="Documentos" actions={<HeaderActions onRefresh={list.reload} onUpload={() => setUploading(true)} withdrawn={withdrawn} onWithdrawn={() => setWithdrawn(!withdrawn)} />} />
      <Notice message={error ?? list.error} />
      {list.data?.truncated && <p role="status" className={styles.truncated}>La lista está cortada: se muestran los primeros {MAX_DOCUMENTS} documentos.</p>}
      <Section title={withdrawn ? 'Documentos retirados' : 'Documentos de la empresa'}>
        {list.data?.documents.length === 0 && <Empty withdrawn={withdrawn} />}
        <SelectionBar count={selected.size} onMark={mark} />
        <Table columns={['Título', 'Estado', 'Temas', 'Subido por', 'Motivo']}>
          {list.data?.documents.map((d) => <DocRow key={d.id} d={d} scannedDocumentsSupported={scannedDocumentsSupported} selected={selected.has(d.id)} onSelect={() => toggle(d.id)}
            onWithdraw={() => withdraw(d)} onRestore={() => reloadAfter(() => api.restore(d.id))}
            onRetry={() => reloadAfter(() => api.retry(d.id))} onUnmark={() => reloadAfter(() => api.unsupersede(d.id))} onToggleCase={() => reloadAfter(() => api.markCase(d.id, !d.caseDocument))}
            onDetail={() => setOpen({ kind: 'detail', document: d })} onSupersede={() => setOpen({ kind: 'supersede', document: d })} />)}
        </Table>
      </Section>
      <UploadDialog companyId={companyId} open={uploading} onClose={() => setUploading(false)} onDone={list.reload} />
      <DocumentDialogs open={open} documents={list.data?.documents ?? []} projects={projects.data ?? []} onClose={() => setOpen(undefined)} onDone={list.reload} />
    </div>
  );
}
