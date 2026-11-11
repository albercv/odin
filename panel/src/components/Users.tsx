import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, tableStyles } from '../ui/Table';
import { useToast } from '../ui/Toast';
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
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label="Nombre"><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <Field label="Correo"><input type="email" required={user.roles.includes('COMPANY_ADMIN')} value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
      <div className={form.buttons}>
        <Button type="submit" variant="primary">Guardar {user.displayName}</Button>
        <Button onClick={() => onDone(false)}>Cancelar {user.displayName}</Button>
      </div>
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
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label="Nueva contraseña"><input type="password" required minLength={12} value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
      <div><Button type="submit" variant="primary">Guardar contraseña de {user.displayName}</Button></div>
    </form>
  );
}

function roleLabel(roles: S['MemberView']['roles']) {
  if (!roles.includes('COMPANY_ADMIN')) return 'Trabajador';
  return roles.includes('WORKER') ? 'Administrador y trabajador' : 'Administrador';
}

type Panel = 'edit' | 'keys' | 'password' | undefined;

function UserPanel({ companyId, u, panel, onDone, onReset }: {
  companyId: string; u: S['MemberView']; panel: Panel; onDone: (saved: boolean) => void; onReset: () => void;
}) {
  if (panel === 'edit') return <EditForm user={u} onDone={onDone} />;
  if (panel === 'keys') return <Keys companyId={companyId} userId={u.userId} />;
  return <ResetPasswordForm user={u} onDone={onReset} />;
}

type RowProps = { companyId: string; u: S['MemberView']; canReset: boolean; onChanged: () => void };

function UserActions({ companyId, u, canReset, onChanged, panel, show }: RowProps & { panel: Panel; show: (p: Panel) => void }) {
  const { error, act } = useAction();
  const confirm = useConfirm();
  const toggle = async () => (await act(() => api.setActive(companyId, u.userId, !u.active))) && onChanged();
  const remove = async () => {
    if (!(await confirm({ message: `¿Quitar a ${u.displayName} de la empresa?`, confirmLabel: 'Quitar', danger: true }))) return;
    if (await act(() => api.removeMember(companyId, u.userId))) onChanged();
  };
  return (
    <>
      <RowActions label={`Más acciones de ${u.displayName}`}>
        <Button variant="ghost" onClick={toggle}>{u.active ? 'Desactivar' : 'Activar'} {u.displayName}</Button>
        <Button variant="ghost" onClick={() => show('edit')}>Editar {u.displayName}</Button>
        <Button variant="danger" onClick={remove}>Quitar {u.displayName}</Button>
        {u.roles.includes('WORKER') && <Button variant="ghost" aria-expanded={panel === 'keys'} onClick={() => show('keys')}>Claves de {u.displayName}</Button>}
        {canReset && <Button variant="ghost" aria-expanded={panel === 'password'} onClick={() => show('password')}>Restablecer contraseña de {u.displayName}</Button>}
      </RowActions>
      <Notice message={error} />
    </>
  );
}

function UserRow(props: RowProps) {
  const { companyId, u, onChanged } = props;
  const [panel, setPanel] = useState<Panel>();
  const toast = useToast();
  const show = (p: Panel) => setPanel(panel === p ? undefined : p);
  const closed = (saved: boolean) => { setPanel(undefined); if (saved) onChanged(); };
  return (
    <>
      <tr>
        <td className={tableStyles.person}><strong>{u.displayName}</strong><span>{u.email ?? 'Sin correo'}</span></td>
        <td>{roleLabel(u.roles)}</td>
        <td><StatusPill tone={u.active ? 'ok' : 'off'}>{u.active ? 'Activo' : 'Desactivado'}</StatusPill></td>
        <td className={tableStyles.actions}><UserActions {...props} panel={panel} show={show} /></td>
      </tr>
      {panel && (
        <tr className={tableStyles.panelRow}>
          <td colSpan={4}>
            <UserPanel companyId={companyId} u={u} panel={panel} onDone={closed} onReset={() => { setPanel(undefined); toast('Contraseña restablecida'); }} />
          </td>
        </tr>
      )}
    </>
  );
}

type Props = { companyId: string; me: S['AdminMe']; addOpen?: boolean; onAddClose?: () => void };

export function Users({ companyId, me, addOpen = false, onAddClose = () => undefined }: Props) {
  const list = useLoad(() => api.members(companyId), [companyId]);
  const isGroup = me.role === 'GROUP_ADMIN';
  return (
    <Section title="Personas" description="Cada persona usa Odín desde su IA con su clave personal.">
      <Notice message={list.error} />
      <Table columns={['Persona', 'Papel', 'Acceso']}>
        {list.data?.map((u) => (
          <UserRow key={u.userId} companyId={companyId} u={u} canReset={isGroup && u.roles.includes('COMPANY_ADMIN')} onChanged={list.reload} />
        ))}
      </Table>
      <FormDialog title="Añadir persona" open={addOpen} onClose={onAddClose}>
        <UserForm companyId={companyId} onDone={() => { onAddClose(); list.reload(); }} />
      </FormDialog>
    </Section>
  );
}
