import { useState } from 'react';
import { MAX_DOCUMENTS, api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { PageHeader } from '../ui/PageHeader';
import { RowAction } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, tableStyles } from '../ui/Table';
import styles from './Documents.module.css';

const STATUS: Record<string, [string, 'ok' | 'off' | 'pending']> = {
  RECEIVED: ['Recibido', 'pending'],
  PROCESSING: ['Procesando', 'pending'],
  READY: ['Listo', 'ok'],
  FAILED: ['Fallido', 'off'],
  PENDING_READER: ['Pendiente de lector', 'pending'],
};

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

function DocRow({ d, onRetry }: { d: S['DocumentView']; onRetry: () => void }) {
  const [label, tone] = STATUS[d.status] ?? [d.status, 'off' as const];
  return (
    <tr>
      <td data-label="Título">{d.title}</td>
      <td data-label="Estado"><StatusPill tone={tone}>{label}</StatusPill></td>
      <td data-label="Motivo">{d.failureReason}</td>
      <td className={tableStyles.actions}>{d.status === 'FAILED' && <RowAction text="Reintentar" target={d.title} onClick={onRetry} />}</td>
    </tr>
  );
}

export function Documents({ companyId }: { companyId: string }) {
  const list = useLoad(() => api.documents(companyId), [companyId]);
  const [uploading, setUploading] = useState(false);
  const { error, act } = useAction();
  const retry = async (id: string) => (await act(() => api.retry(id))) && list.reload();
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
          {list.data?.documents.map((d) => <DocRow key={d.id} d={d} onRetry={() => retry(d.id)} />)}
        </Table>
      </Section>
      <UploadDialog companyId={companyId} open={uploading} onClose={() => setUploading(false)} onDone={list.reload} />
    </div>
  );
}
