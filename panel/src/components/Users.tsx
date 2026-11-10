import { useState } from 'react';
import { api, type S } from '../api/client';
import { Keys } from './Keys';
import { Notice } from './Notice';
import { UserForm } from './UserForm';
import { useAction, useLoad } from './useLoad';

function EditForm({ user, onDone }: { user: S['MemberView']; onDone: (saved: boolean) => void }) {
  const [name, setName] = useState(user.displayName);
  const [email, setEmail] = useState(user.email ?? '');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.updateUser(user.userId, { displayName: name, email }))) onDone(true);
  };
  return (
    <form onSubmit={submit}>
      <Notice message={error} />
      <label>Nombre <input required value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label>Correo <input type="email" required={user.roles.includes('COMPANY_ADMIN')} value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      <button type="submit">Guardar {user.displayName}</button>
      <button type="button" onClick={() => onDone(false)}>Cancelar {user.displayName}</button>
    </form>
  );
}

function ResetPasswordForm({ user, onDone }: { user: S['MemberView']; onDone: () => void }) {
  const [password, setPassword] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.setPassword(user.userId, { newPassword: password }))) onDone();
  };
  return (
    <form onSubmit={submit}>
      <Notice message={error} />
      <label>Nueva contraseña <input type="password" required minLength={12} value={password} onChange={(e) => setPassword(e.target.value)} /></label>
      <button type="submit">Guardar contraseña de {user.displayName}</button>
    </form>
  );
}

function roleLabel(roles: S['MemberView']['roles']) {
  if (!roles.includes('COMPANY_ADMIN')) return 'Trabajador';
  return roles.includes('WORKER') ? 'Administrador y trabajador' : 'Administrador';
}

type Panel = 'edit' | 'keys' | 'password' | undefined;

function UserRow({ companyId, u, canReset, onChanged }: { companyId: string; u: S['MemberView']; canReset: boolean; onChanged: () => void }) {
  const [panel, setPanel] = useState<Panel>();
  const [passwordReset, setPasswordReset] = useState(false);
  const { error, act } = useAction();
  const toggle = async () => (await act(() => api.setActive(companyId, u.userId, !u.active))) && onChanged();
  const remove = async () => {
    if (!window.confirm(`¿Quitar a ${u.displayName} de la empresa?`)) return;
    if (await act(() => api.removeMember(companyId, u.userId))) onChanged();
  };
  const show = (p: Panel) => {
    setPasswordReset(false);
    setPanel(panel === p ? undefined : p);
  };
  const isWorker = u.roles.includes('WORKER');
  if (panel === 'edit') return <li><EditForm user={u} onDone={(saved) => { setPanel(undefined); if (saved) onChanged(); }} /></li>;
  return (
    <li>
      {u.displayName} · {u.email ?? 'sin correo'} · {roleLabel(u.roles)} · {u.active ? 'activo' : 'desactivado'}
      <Notice message={error} />
      <button type="button" onClick={toggle}>{u.active ? 'Desactivar' : 'Activar'} {u.displayName}</button>
      <button type="button" onClick={() => show('edit')}>Editar {u.displayName}</button>
      <button type="button" onClick={remove}>Quitar {u.displayName}</button>
      {isWorker && <button type="button" aria-expanded={panel === 'keys'} onClick={() => show('keys')}>Claves de {u.displayName}</button>}
      {canReset && <button type="button" aria-expanded={panel === 'password'} onClick={() => show('password')}>Restablecer contraseña de {u.displayName}</button>}
      {panel === 'keys' && <Keys companyId={companyId} userId={u.userId} />}
      {panel === 'password' && <ResetPasswordForm user={u} onDone={() => { setPanel(undefined); setPasswordReset(true); }} />}
      {passwordReset && <span role="status">Contraseña restablecida</span>}
    </li>
  );
}

export function Users({ companyId, me }: { companyId: string; me: S['AdminMe'] }) {
  const list = useLoad(() => api.members(companyId), [companyId]);
  const isGroup = me.role === 'GROUP_ADMIN';
  return (
    <section>
      <h2>Usuarios</h2>
      <Notice message={list.error} />
      <ul>
        {list.data?.map((u) => (
          <UserRow key={u.userId} companyId={companyId} u={u} canReset={isGroup && u.roles.includes('COMPANY_ADMIN')}
            onChanged={list.reload} />
        ))}
      </ul>
      <UserForm companyId={companyId} onDone={list.reload} />
    </section>
  );
}
