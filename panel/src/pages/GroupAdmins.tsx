import { useState } from 'react';
import { api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { confirmDeactivatePerson, confirmDeletePerson } from '../components/personConfirmations';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { PageHeader } from '../ui/PageHeader';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, Td, Tr, tableStyles } from '../ui/Table';
import { useToast } from '../ui/Toast';

type Admin = S['GroupAdminView'];

const ALREADY_EXISTED = 'Esta persona ya existía en el grupo: ahora también administra el grupo.';
const KEPT_PASSWORD = ' Conserva su contraseña.';

type Draft = { firstName: string; lastName: string; email: string; password: string };
const EMPTY: Draft = { firstName: '', lastName: '', email: '', password: '' };

function AddForm({ onDone }: { onDone: () => void }) {
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const { error, act } = useAction();
  const toast = useToast();
  const set = (changes: Partial<Draft>) => setDraft({ ...draft, ...changes });
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const { password, ...person } = draft;
    let added: S['GroupAdminAdded'] | undefined;
    if (!(await act(async () => { added = await api.addGroupAdmin({ ...person, ...(password && { password }) }); }))) return;
    if (added?.alreadyExisted) toast(ALREADY_EXISTED + (added.keptPassword ? KEPT_PASSWORD : ''));
    onDone();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label="Nombre"><input required value={draft.firstName} onChange={(e) => set({ firstName: e.target.value })} /></Field>
      <Field label="Apellidos"><input required value={draft.lastName} onChange={(e) => set({ lastName: e.target.value })} /></Field>
      <Field label="Correo"><input type="email" required value={draft.email} onChange={(e) => set({ email: e.target.value })} /></Field>
      <Field label="Contraseña"><input type="password" minLength={12} value={draft.password} onChange={(e) => set({ password: e.target.value })} /></Field>
      <p className={form.hint}>La contraseña solo hace falta si la persona no tiene ya una.</p>
      <div><Button type="submit" variant="primary">Añadir administrador</Button></div>
    </form>
  );
}

function useAdminActions(u: Admin, onChanged: () => void, act: ReturnType<typeof useAction>['act']) {
  const confirm = useConfirm();
  const run = async (message: string, confirmLabel: string, fn: () => Promise<unknown>, danger = true) => {
    if (await confirm({ message, confirmLabel, danger }) && await act(fn)) onChanged();
  };
  const toggle = () => run(
    u.active
      ? confirmDeactivatePerson(u.displayName)
      : `¿Activar a ${u.displayName}? Vuelve a entrar en el panel y sus claves vuelven a valer.`,
    u.active ? 'Desactivar' : 'Activar', () => api.updateUser(u.userId, { active: !u.active }), u.active);
  const revoke = () => run(
    `¿Quitar la administración del grupo a ${u.displayName}? Sigue existiendo, con sus pertenencias, pero ya no administra el grupo.`,
    'Quitar administración', () => api.revokeGroupAdmin(u.userId));
  const erase = () => run(
    confirmDeletePerson(u.displayName),
    'Borrar', () => api.deleteUser(u.userId));
  return { toggle, revoke, erase };
}

function AdminRow({ u, own, onChanged }: { u: Admin; own: boolean; onChanged: () => void }) {
  const { error, act } = useAction();
  const { toggle, revoke, erase } = useAdminActions(u, onChanged, act);
  return (
    <Tr>
      <Td label="Código"><code>{u.personCode}</code></Td>
      <Td label="Persona" className={tableStyles.person}><strong>{u.displayName}</strong><span>{u.email}</span></Td>
      <Td label="Estado"><StatusPill tone={u.active ? 'ok' : 'off'}>{u.active ? 'Activo' : 'Desactivado'}</StatusPill></Td>
      <Td className={tableStyles.actions}>
        {!own && (
          <RowActions label={`Más acciones de ${u.displayName}`}>
            <RowAction variant="ghost" text={u.active ? 'Desactivar' : 'Activar'} target={u.displayName} onClick={toggle} />
            <RowAction variant="danger" text="Quitar administración" target={u.displayName} onClick={revoke} />
            <RowAction variant="danger" text="Borrar" target={u.displayName} onClick={erase} />
          </RowActions>
        )}
        <Notice message={error} />
      </Td>
    </Tr>
  );
}

export function GroupAdmins({ me }: { me: S['AdminMe'] }) {
  const list = useLoad(api.groupAdmins, []);
  const [adding, setAdding] = useState(false);
  return (
    <div>
      <PageHeader title="Administración del grupo"
        actions={<Button variant="primary" icon="plus" onClick={() => setAdding(true)}>Añadir administrador del grupo</Button>} />
      <Notice message={list.error} />
      <Section title="Administradores del grupo" description="Todos tienen la misma autoridad. Siempre tiene que quedar uno activo.">
        <Table columns={['Código', 'Persona', 'Estado']}>
          {list.data?.map((u) => <AdminRow key={u.userId} u={u} own={u.userId === me.userId} onChanged={list.reload} />)}
        </Table>
      </Section>
      <FormDialog title="Añadir administrador del grupo" open={adding} onClose={() => setAdding(false)}>
        <AddForm onDone={() => { setAdding(false); list.reload(); }} />
      </FormDialog>
    </div>
  );
}
