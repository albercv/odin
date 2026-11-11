import { useState } from 'react';
import { api, type S } from '../api/client';
import { RowAction } from '../ui/RowActions';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { Notice } from './Notice';
import { RolesFields, choiceOf, rolesOf, type RoleChoice } from './RolesFields';
import { useAction } from './useLoad';

type Member = S['MemberView'];

export function EditForm({ user, onDone }: { user: Member; onDone: (saved: boolean) => void }) {
  const [firstName, setFirstName] = useState(user.firstName);
  const [lastName, setLastName] = useState(user.lastName ?? '');
  const [email, setEmail] = useState(user.email ?? '');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.updateUser(user.userId, { firstName, ...(lastName !== (user.lastName ?? '') && { lastName }), ...(email !== (user.email ?? '') && { email }) }))) onDone(true);
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label="Nombre"><input required value={firstName} onChange={(e) => setFirstName(e.target.value)} /></Field>
      <Field label="Apellidos"><input value={lastName} onChange={(e) => setLastName(e.target.value)} /></Field>
      <Field label="Correo"><input type="email" required={user.roles.includes('COMPANY_ADMIN')} value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
      <div className={form.buttons}>
        <RowAction type="submit" variant="primary" text="Guardar" target={user.displayName} />
        <RowAction text="Cancelar" target={user.displayName} onClick={() => onDone(false)} />
      </div>
    </form>
  );
}

type RolesProps = { companyId: string; user: Member; canGiveAdmin: boolean; onDone: (saved: boolean) => void };

export function RolesForm({ companyId, user, canGiveAdmin, onDone }: RolesProps) {
  const [choice, setChoice] = useState<RoleChoice>(choiceOf(user.roles));
  const [password, setPassword] = useState('');
  const [roleError, setRoleError] = useState<string>();
  const { error, act } = useAction();
  // Becoming an administrator needs a password only if the person has none yet; never overwrite an existing one.
  const needsPassword = choice.admin && !user.roles.includes('COMPANY_ADMIN') && !user.hasPassword;
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!choice.admin && !choice.worker) return setRoleError('Elige al menos un papel');
    setRoleError(undefined);
    const changes = { roles: rolesOf(choice), ...(needsPassword && { password }) };
    if (await act(() => api.updateMember(companyId, user.userId, changes))) onDone(true);
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <RolesFields choice={choice} onChange={setChoice} canGiveAdmin={canGiveAdmin} error={roleError} />
      {needsPassword && <Field label="Contraseña"><input type="password" required minLength={12} value={password} onChange={(e) => setPassword(e.target.value)} /></Field>}
      <div className={form.buttons}>
        <RowAction type="submit" variant="primary" text="Guardar" label="Guardar papeles de" target={user.displayName} />
        <RowAction text="Cancelar" target={user.displayName} onClick={() => onDone(false)} />
      </div>
    </form>
  );
}

export function ResetPasswordForm({ user, onDone }: { user: Member; onDone: () => void }) {
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
      <div><RowAction type="submit" variant="primary" text="Guardar" label="Guardar contraseña de" target={user.displayName} /></div>
    </form>
  );
}
