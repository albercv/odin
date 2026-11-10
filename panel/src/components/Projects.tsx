import { useState } from 'react';
import { api, type S } from '../api/client';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';

function AddProject({ companyId, onDone }: { companyId: string; onDone: () => void }) {
  const [name, setName] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.createProject(companyId, name))) {
      setName('');
      onDone();
    }
  };
  return (
    <form onSubmit={submit}>
      <Notice message={error} />
      <label>Nombre del proyecto <input required value={name} onChange={(e) => setName(e.target.value)} /></label>
      <button type="submit">Añadir</button>
    </form>
  );
}

function RenameForm({ project, companyId, onDone }: { project: S['ProjectView']; companyId: string; onDone: () => void }) {
  const [name, setName] = useState(project.name);
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.renameProject(companyId, project.id, name))) onDone();
  };
  return (
    <form onSubmit={submit}>
      <Notice message={error} />
      <label>Nombre <input required value={name} onChange={(e) => setName(e.target.value)} /></label>
      <button type="submit">Guardar {project.name}</button>
      <button type="button" onClick={onDone}>Cancelar {project.name}</button>
    </form>
  );
}

function ProjectRow({ project, companyId, onChanged }: { project: S['ProjectView']; companyId: string; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const { error, act } = useAction();
  const remove = async () => {
    if (!window.confirm(`¿Borrar el proyecto ${project.name}? Sus documentos se conservan.`)) return;
    if (await act(() => api.deleteProject(companyId, project.id))) onChanged();
  };
  const done = () => { setEditing(false); onChanged(); };
  if (editing) return <li><RenameForm project={project} companyId={companyId} onDone={done} /></li>;
  return (
    <li>
      {project.name}
      <Notice message={error} />
      <button type="button" onClick={() => setEditing(true)}>Renombrar {project.name}</button>
      <button type="button" onClick={remove}>Borrar {project.name}</button>
    </li>
  );
}

export function Projects({ companyId }: { companyId: string }) {
  const list = useLoad(() => api.projects(companyId), [companyId]);
  return (
    <section>
      <h2>Proyectos</h2>
      <Notice message={list.error} />
      <ul>{list.data?.map((p) => <ProjectRow key={p.id} project={p} companyId={companyId} onChanged={list.reload} />)}</ul>
      <AddProject companyId={companyId} onDone={list.reload} />
    </section>
  );
}
