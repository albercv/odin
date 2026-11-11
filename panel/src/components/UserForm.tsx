import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { Notice } from './Notice';
import { useAction } from './useLoad';

type Role = 'WORKER' | 'COMPANY_ADMIN';

function newMemberBody(name: string, email: string, role: Role, password: string): S['AddMember'] {
  const body: S['AddMember'] = { displayName: name, roles: [role] };
  if (email) body.email = email;
  if (role === 'COMPANY_ADMIN') body.password = password;
  return body;
}

function PasswordField({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return <Field label="Contraseña"><input type="password" required minLength={12} value={value} onChange={(e) => onChange(e.target.value)} /></Field>;
}

function RoleSelect({ value, onChange }: { value: Role; onChange: (r: Role) => void }) {
  return (
    <Field label="Rol">
      <select value={value} onChange={(e) => onChange(e.target.value as Role)}>
        <option value="WORKER">Trabajador</option>
        <option value="COMPANY_ADMIN">Administrador de empresa</option>
      </select>
    </Field>
  );
}

export function UserForm({ companyId, onDone }: { companyId: string; onDone: () => void }) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('WORKER');
  const { error, act } = useAction();
  const isAdmin = role === 'COMPANY_ADMIN';
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(() => api.addMember(companyId, newMemberBody(name, email, role, password))))) return;
    setName('');
    setEmail('');
    setPassword('');
    onDone();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label="Nombre"><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <Field label="Correo"><input type="email" required={isAdmin} value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
      {isAdmin && <PasswordField value={password} onChange={setPassword} />}
      <RoleSelect value={role} onChange={setRole} />
      <div><Button type="submit" variant="primary">Crear usuario</Button></div>
    </form>
  );
}
