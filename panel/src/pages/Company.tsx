import { useState } from 'react';
import { api, type S } from '../api/client';
import { Keys } from '../components/Keys';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';

function AddForm({ label, onAdd }: { label: string; onAdd: (name: string) => Promise<unknown> }) {
  const [name, setName] = useState('');
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    await onAdd(name);
    setName('');
  };
  return (
    <form onSubmit={submit}>
      <label>{label} <input required value={name} onChange={(e) => setName(e.target.value)} /></label>
      <button type="submit">Añadir</button>
    </form>
  );
}

function Projects({ companyId }: { companyId: string }) {
  const list = useLoad(() => api.projects(companyId), [companyId]);
  const { error, act } = useAction();
  const add = (name: string) => act(() => api.createProject(companyId, name)).then(list.reload);
  return (
    <section>
      <h2>Proyectos</h2>
      <Notice message={error ?? list.error} />
      <ul>{list.data?.map((p) => <li key={p.id}>{p.name}</li>)}</ul>
      <AddForm label="Nombre del proyecto" onAdd={add} />
    </section>
  );
}

function NewUser({ companyId, onDone }: { companyId: string; onDone: () => void }) {
  const [f, setF] = useState<S['CreateUser']>({ displayName: '', email: '', role: 'WORKER', password: '' });
  const { error, act } = useAction();
  const set = (k: string) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.createUser(companyId, f))) onDone();
  };
  return (
    <form onSubmit={submit}>
      <Notice message={error} />
      <label>Nombre <input required value={f.displayName} onChange={set('displayName')} /></label>
      <label>Correo <input type="email" required value={f.email} onChange={set('email')} /></label>
      <label>Contraseña <input type="password" required value={f.password} onChange={set('password')} /></label>
      <label>Rol
        <select value={f.role} onChange={(e) => setF({ ...f, role: e.target.value as S['CreateUser']['role'] })}>
          <option value="WORKER">Trabajador</option>
          <option value="COMPANY_ADMIN">Administrador de empresa</option>
        </select>
      </label>
      <button type="submit">Crear usuario</button>
    </form>
  );
}

function UserRow({ u, onChanged }: { u: S['UserView']; onChanged: () => void }) {
  const [open, setOpen] = useState(false);
  const { error, act } = useAction();
  const toggle = async () => (await act(() => api.setActive(u.id, !u.active))) && onChanged();
  return (
    <li>
      {u.displayName} · {u.role === 'WORKER' ? 'Trabajador' : 'Administrador'} · {u.active ? 'activo' : 'desactivado'}
      <Notice message={error} />
      <button type="button" onClick={toggle}>{u.active ? 'Desactivar' : 'Activar'} {u.displayName}</button>
      <button type="button" aria-expanded={open} onClick={() => setOpen(!open)}>Claves de {u.displayName}</button>
      {open && <Keys userId={u.id} />}
    </li>
  );
}

function Users({ companyId }: { companyId: string }) {
  const list = useLoad(() => api.users(companyId), [companyId]);
  return (
    <section>
      <h2>Usuarios</h2>
      <Notice message={list.error} />
      <ul>{list.data?.map((u) => <UserRow key={u.id} u={u} onChanged={list.reload} />)}</ul>
      <NewUser companyId={companyId} onDone={list.reload} />
    </section>
  );
}

export function Company({ companyId }: { companyId: string }) {
  const companies = useLoad(api.companies, []);
  const name = companies.data?.find((c) => c.id === companyId)?.name ?? 'Empresa';
  return (
    <div>
      <h1>{name}</h1>
      <Projects companyId={companyId} />
      <Users companyId={companyId} />
    </div>
  );
}
