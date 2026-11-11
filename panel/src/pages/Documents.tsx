import { useState } from 'react';
import { MAX_DOCUMENTS, api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';

const STATUS: Record<string, string> = {
  RECEIVED: 'Recibido',
  PROCESSING: 'Procesando',
  READY: 'Listo',
  FAILED: 'Fallido',
  PENDING_READER: 'Pendiente de lector',
};

function UploadForm({ companyId, onDone }: { companyId: string; onDone: () => void }) {
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const [file, setFile] = useState<File>();
  const [title, setTitle] = useState('');
  const [projectId, setProjectId] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (file && (await act(() => api.upload(companyId, file, title, projectId)))) onDone();
  };
  return (
    <form onSubmit={submit}>
      <Notice message={error} />
      <label>Fichero <input type="file" required onChange={(e) => setFile(e.target.files?.[0])} /></label>
      <label>Título (opcional) <input value={title} onChange={(e) => setTitle(e.target.value)} /></label>
      <label>Proyecto
        <select value={projectId} onChange={(e) => setProjectId(e.target.value)}>
          <option value="">Sin proyecto</option>
          {projects.data?.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </label>
      <button type="submit">Subir documento</button>
    </form>
  );
}

function DocRow({ d, onRetry }: { d: S['DocumentView']; onRetry: () => void }) {
  return (
    <tr>
      <td>{d.title}</td>
      <td>{STATUS[d.status] ?? d.status}</td>
      <td>{d.failureReason}</td>
      <td>{d.status === 'FAILED' && <button type="button" onClick={onRetry}>Reintentar</button>}</td>
    </tr>
  );
}

export function Documents({ companyId }: { companyId: string }) {
  const list = useLoad(() => api.documents(companyId), [companyId]);
  const { error, act } = useAction();
  const retry = async (id: string) => (await act(() => api.retry(id))) && list.reload();
  return (
    <section>
      <h1>Documentos</h1>
      <Notice message={error ?? list.error} />
      <UploadForm companyId={companyId} onDone={list.reload} />
      <button type="button" onClick={list.reload}>Actualizar</button>
      {list.data?.truncated && <p role="status">La lista está cortada: se muestran los primeros {MAX_DOCUMENTS} documentos.</p>}
      <table>
        <thead><tr><th>Título</th><th>Estado</th><th>Motivo</th><th>Acciones</th></tr></thead>
        <tbody>{list.data?.documents.map((d) => <DocRow key={d.id} d={d} onRetry={() => retry(d.id)} />)}</tbody>
      </table>
    </section>
  );
}
