import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { RowAction } from '../ui/RowActions';
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
  const change = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const restricted = !e.target.checked;
    const message = `¿Cerrar ${project.name}? Solo lo verán sus miembros, y ahora no tiene ninguno.`;
    if (restricted && project.memberCount === 0 && !(await confirm({ message, confirmLabel: 'Cerrar' }))) return;
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
  const [chosen, setChosen] = useState('');
  const { error, act } = useAction();
  const taken = new Set(members.map((m) => m.userId));
  const free = people.data?.filter((p) => p.active && p.roles.includes('WORKER') && !taken.has(p.userId)) ?? [];
  const add = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(() => api.addProjectMember(companyId, project.id, chosen)))) return;
    setChosen('');
    reload();
  };
  return (
    <form className={styles.add} onSubmit={add}>
      <Notice message={error ?? people.error} />
      <Field label="Persona">
        <select value={chosen} onChange={(e) => setChosen(e.target.value)}>
          <option value="">Elige una persona</option>
          {free.map((p) => <option key={p.userId} value={p.userId}>{p.displayName}</option>)}
        </select>
      </Field>
      <Button type="submit" disabled={!chosen}>Añadir</Button>
    </form>
  );
}

/** Who can see a closed project: the visibility box and its members, loaded only when opened. */
export function ProjectMembers(props: Props) {
  const { companyId, project, onChanged } = props;
  const members = useLoad(() => api.projectMembers(companyId, project.id), [companyId, project.id]);
  const { error, act } = useAction();
  const reload = () => { members.reload(); onChanged(); };
  const remove = async (userId: string) => (await act(() => api.removeProjectMember(companyId, project.id, userId))) && reload();
  return (
    <section aria-label={`Miembros de ${project.name}`} className={styles.accesses}>
      <VisibleToAll {...props} />
      <Notice message={error ?? members.error} />
      <ul className={styles.list}>
        {members.data?.map((m) => (
          <li key={m.userId} className={styles.item}>
            <span>{m.displayName}</span>
            <RowAction variant="danger" text="Quitar" target={m.displayName} onClick={() => remove(m.userId)} />
          </li>
        ))}
      </ul>
      <AddMember {...props} members={members.data ?? []} reload={reload} />
    </section>
  );
}
