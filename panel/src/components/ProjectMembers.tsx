import { useRef } from 'react';
import { api, type S } from '../api/client';
import { useConfirm } from '../ui/ConfirmDialog';
import form from '../ui/Form.module.css';
import { RowAction } from '../ui/RowActions';
import { useToast } from '../ui/Toast';
import { AddPicker } from './AddPicker';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './MemberAccesses.module.css';

type Project = S['ProjectView'];
type Person = S['ProjectMemberView'];

/** Text of the «Quién lo ve» column. */
export function whoSees(project: Project) {
  const count = project.memberCount ?? 0;
  if (!project.restricted) return 'Toda la empresa';
  if (count === 0) return 'Nadie todavía';
  return count === 1 ? '1 miembro' : `${count} miembros`;
}

type Props = { companyId: string; project: Project; onChanged: () => void };
type WithMembers = Props & { members: Person[]; reload: () => void };

function VisibleToAll({ companyId, project, onChanged }: Props) {
  const { error, act } = useAction();
  const confirm = useConfirm();
  // The list may have changed since the panel opened (e.g. from the person's «Accesos»), so ask again before deciding.
  const freshMemberCount = async () => {
    const found: { count?: number } = {};
    await act(async () => { found.count = (await api.projectMembers(companyId, project.id)).length; });
    return found.count;
  };
  const change = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const restricted = !e.target.checked;
    const message = `¿Cerrar ${project.name}? Solo lo verán sus miembros, y ahora no tiene ninguno.`;
    const count = restricted ? await freshMemberCount() : 0;
    if (count === undefined) return;
    if (restricted && count === 0 && !(await confirm({ message, confirmLabel: 'Cerrar' }))) return;
    if (await act(() => api.updateProject(companyId, project.id, { restricted }))) onChanged();
  };
  return (
    <>
      <Notice message={error} />
      <label className={form.check}><input type="checkbox" checked={!project.restricted} onChange={change} />Visible para toda la empresa</label>
    </>
  );
}

function AddMember({ companyId, project, members, reload }: WithMembers) {
  const people = useLoad(() => api.members(companyId), [companyId]);
  const { error, act } = useAction();
  const toast = useToast();
  const taken = new Set(members.map((m) => m.userId));
  const free = people.data?.filter((p) => p.active && p.roles.includes('WORKER') && !taken.has(p.userId)) ?? [];
  const add = async (userId: string) => {
    if (!(await act(() => api.addProjectMember(companyId, project.id, userId)))) return false;
    toast(`Añadido ${free.find((p) => p.userId === userId)?.displayName}`);
    reload();
    return true;
  };
  return (
    <>
      <Notice message={error ?? people.error} />
      <AddPicker label="Persona" placeholder="Elige una persona" options={free.map((p) => ({ id: p.userId, name: p.displayName }))} onAdd={add} />
    </>
  );
}

/** Who can see a closed project: the visibility box and its members, loaded only when opened. */
export function ProjectMembers(props: Props) {
  const { companyId, project, onChanged } = props;
  const members = useLoad(() => api.projectMembers(companyId, project.id), [companyId, project.id]);
  const list = useRef<HTMLUListElement>(null);
  const toast = useToast();
  const { error, act } = useAction();
  const reload = () => { members.reload(); onChanged(); };
  const remove = async (m: Person) => {
    if (!(await act(() => api.removeProjectMember(companyId, project.id, m.userId)))) return;
    toast(`Quitado ${m.displayName}`);
    reload();
    list.current?.focus();
  };
  return (
    <section aria-label={`Miembros de ${project.name}`} className={styles.accesses}>
      <VisibleToAll {...props} />
      <Notice message={error ?? members.error} />
      <ul ref={list} tabIndex={-1} aria-label="Miembros del proyecto" className={styles.list}>
        {members.data?.map((m) => (
          <li key={m.userId} className={styles.item}>
            <span>{m.displayName}</span>
            <RowAction variant="danger" text="Quitar" target={m.displayName} onClick={() => remove(m)} />
          </li>
        ))}
      </ul>
      <AddMember {...props} members={members.data ?? []} reload={reload} />
    </section>
  );
}
