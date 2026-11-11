import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
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
    if (await act(() => api.updateUser(user.userId, { firstName, ...(lastName !== (user.lastName ?? '') && { lastName }), email }))) onDone(true);
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label="Nombre"><input required value={firstName} onChange={(e) => setFirstName(e.target.value)} /></Field>
      <Field label="Apellidos"><input value={lastName} onChange={(e) => setLastName(e.target.value)} /></Field>
      <Field label="Correo"><input type="email" required={user.roles.includes('COMPANY_ADMIN')} value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
      <div className={form.buttons}>
        <Button type="submit" variant="primary">Guardar {user.displayName}</Button>
        <Button onClick={() => onDone(false)}>Cancelar {user.displayName}</Button>
      </div>
    </form>
  );
}

type RolesProps = { companyId: string; user: Member; canGiveAdmin: boolean; onDone: (saved: boolean) => void };

export function RolesForm({ companyId, user, canGiveAdmin, onDone }: RolesProps) {
  const [choice, setChoice] = useState<RoleChoice>(choiceOf(user.roles));
  const [roleError, setRoleError] = useState<string>();
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!choice.admin && !choice.worker) return setRoleError('Elige al menos un papel');
    setRoleError(undefined);
    if (await act(() => api.updateMember(companyId, user.userId, { roles: rolesOf(choice) }))) onDone(true);
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <RolesFields choice={choice} onChange={setChoice} canGiveAdmin={canGiveAdmin} error={roleError} />
      <div className={form.buttons}>
        <Button type="submit" variant="primary">Guardar papeles de {user.displayName}</Button>
        <Button onClick={() => onDone(false)}>Cancelar {user.displayName}</Button>
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
      <div><Button type="submit" variant="primary">Guardar contraseña de {user.displayName}</Button></div>
    </form>
  );
}
