import { useState } from 'react';
import { MAX_DOCUMENTS, api, type S } from '../api/client';
import { DocumentDetail } from '../components/DocumentDetail';
import { Notice } from '../components/Notice';
import { SupersedeDialog } from '../components/SupersedeDialog';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { PageHeader } from '../ui/PageHeader';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, Td, Tr, tableStyles } from '../ui/Table';
import { NO_READER_LABEL, STATUS } from './documentStatus';
import styles from './Documents.module.css';

function UploadDialog({ companyId, open, onClose, onDone }: { companyId: string; open: boolean; onClose: () => void; onDone: () => void }) {
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const [file, setFile] = useState<File>();
  const [title, setTitle] = useState('');
  const [projectId, setProjectId] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file || !(await act(() => api.upload(companyId, file, title, projectId)))) return;
    setTitle('');
    onClose();
    onDone();
  };
  return (
    <FormDialog title="Subir documento" open={open} onClose={onClose}>
      <form className={form.form} onSubmit={submit}>
        <Notice message={error} />
        <Field label="Fichero"><input type="file" required onChange={(e) => setFile(e.target.files?.[0])} /></Field>
        <Field label="Título (opcional)"><input value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
        <Field label="Proyecto">
          <select value={projectId} onChange={(e) => setProjectId(e.target.value)}>
            <option value="">Sin proyecto</option>
            {projects.data?.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </Field>
        <div><Button type="submit" variant="primary">Subir</Button></div>
      </form>
    </FormDialog>
  );
}

type RowHandlers = { onRetry: () => void; onDetail: () => void; onSupersede: () => void; onUnmark: () => void };

function DocActions({ d, onDetail, onSupersede, onUnmark }: { d: S['DocumentView'] } & RowHandlers) {
  return (
    <RowActions label={`Más acciones de ${d.title}`}>
      <RowAction variant="ghost" text="Ver detalle" target={d.title} label="Ver detalle de" onClick={onDetail} />
      <RowAction variant="ghost" text="Marcar como sustituido…" label="Marcar como sustituido" target={d.title} onClick={onSupersede} />
      {d.supersededBy && <RowAction variant="ghost" text="Quitar marca de sustituido" label="Quitar marca de sustituido de" target={d.title} onClick={onUnmark} />}
    </RowActions>
  );
}

function DocRow({ d, scannedDocumentsSupported, ...handlers }: { d: S['DocumentView']; scannedDocumentsSupported: boolean } & RowHandlers) {
  const [known, tone] = STATUS[d.status] ?? [d.status, 'off' as const];
  const label = d.status === 'PENDING_READER' && !scannedDocumentsSupported ? NO_READER_LABEL : known;
  return (
    <Tr>
      <Td label="Título">
        {d.title}
        {d.supersededBy && <div className={styles.superseded}><StatusPill tone="off">Sustituido</StatusPill><span>{`por ${d.supersededBy.title}`}</span></div>}
      </Td>
      <Td label="Estado">
        <StatusPill tone={tone}>{label}</StatusPill>
        {d.pagesWithoutText.length > 0 && <div className={styles.superseded}>{`Páginas sin texto, no leídas: ${d.pagesWithoutText.join(', ')}`}</div>}
      </Td>
      <Td label="Motivo">{d.failureReason}</Td>
      <Td className={tableStyles.actions}>
        {d.status === 'FAILED' && <RowAction text="Reintentar" target={d.title} onClick={handlers.onRetry} />}
        <DocActions d={d} {...handlers} />
      </Td>
    </Tr>
  );
}

type Open = { kind: 'detail' | 'supersede'; document: S['DocumentView'] };

function DocumentDialogs({ open, documents, projects, onClose, onDone }: { open?: Open; documents: S['DocumentView'][]; projects: S['ProjectView'][]; onClose: () => void; onDone: () => void }) {
  if (open?.kind === 'supersede') return <SupersedeDialog document={open.document} documents={documents} projects={projects} onClose={onClose} onDone={onDone} />;
  if (open?.kind === 'detail') return <DocumentDetail document={open.document} projects={projects} onClose={onClose} />;
  return null;
}

export function Documents({ companyId, scannedDocumentsSupported = false }: { companyId: string; scannedDocumentsSupported?: boolean }) {
  const list = useLoad(() => api.documents(companyId), [companyId]);
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const [uploading, setUploading] = useState(false);
  const [open, setOpen] = useState<Open>();
  const { error, act } = useAction();
  const reloadAfter = async (fn: () => Promise<unknown>) => (await act(fn)) && list.reload();
  const actions = (
    <div className={styles.actions}>
      <Button onClick={list.reload}>Actualizar</Button>
      <Button variant="primary" icon="upload" onClick={() => setUploading(true)}>Subir documento</Button>
    </div>
  );
  return (
    <div>
      <PageHeader title="Documentos" actions={actions} />
      <Notice message={error ?? list.error} />
      {list.data?.truncated && <p role="status" className={styles.truncated}>La lista está cortada: se muestran los primeros {MAX_DOCUMENTS} documentos.</p>}
      <Section title="Documentos de la empresa">
        {list.data?.documents.length === 0 && <EmptyState title="Aún no hay documentos" description="Sube el primero con «Subir documento»." />}
        <Table columns={['Título', 'Estado', 'Motivo']}>
          {list.data?.documents.map((d) => <DocRow key={d.id} d={d} scannedDocumentsSupported={scannedDocumentsSupported} onRetry={() => reloadAfter(() => api.retry(d.id))} onUnmark={() => reloadAfter(() => api.unsupersede(d.id))}
            onDetail={() => setOpen({ kind: 'detail', document: d })} onSupersede={() => setOpen({ kind: 'supersede', document: d })} />)}
        </Table>
      </Section>
      <UploadDialog companyId={companyId} open={uploading} onClose={() => setUploading(false)} onDone={list.reload} />
      <DocumentDialogs open={open} documents={list.data?.documents ?? []} projects={projects.data ?? []} onClose={() => setOpen(undefined)} onDone={list.reload} />
    </div>
  );
}
