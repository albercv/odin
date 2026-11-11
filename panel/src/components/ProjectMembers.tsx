import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './MemberAccesses.module.css';

type Project = S['ProjectView'];
type Person = S['ProjectMemberView'];

/** Text of the «Quién lo ve» column. */
export function whoSees(project: Project, memberCount: number) {
  if (!project.restricted) return 'Toda la empresa';
  if (memberCount === 0) return 'Nadie todavía';
  return memberCount === 1 ? '1 miembro' : `${memberCount} miembros`;
}

type Props = { companyId: string; project: Project; members: Person[]; onChanged: () => void };

function VisibleToAll({ companyId, project, members, onChanged }: Props) {
  const { error, act } = useAction();
  const confirm = useConfirm();
  const change = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const restricted = !e.target.checked;
    const message = `¿Cerrar ${project.name}? Solo lo verán sus miembros, y ahora no tiene ninguno.`;
    if (restricted && members.length === 0 && !(await confirm({ message, confirmLabel: 'Cerrar' }))) return;
    if (await act(() => api.updateProject(companyId, project.id, { restricted }))) onChanged();
  };
  return (
    <>
      <Notice message={error} />
      <label className={form.check}><input type="checkbox" checked={!project.restricted} onChange={change} />Visible para toda la empresa</label>
    </>
  );
}

function AddMember({ companyId, project, members, onChanged }: Props) {
  const people = useLoad(() => api.members(companyId), [companyId]);
  const [chosen, setChosen] = useState('');
  const { error, act } = useAction();
  const taken = new Set(members.map((m) => m.userId));
  const free = people.data?.filter((p) => p.active && p.roles.includes('WORKER') && !taken.has(p.userId)) ?? [];
  const add = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(() => api.addProjectMember(companyId, project.id, chosen)))) return;
    setChosen('');
    onChanged();
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

/** Who can see a closed project: the visibility box and its members. */
export function ProjectMembers(props: Props) {
  const { companyId, project, members, onChanged } = props;
  const { error, act } = useAction();
  const remove = async (userId: string) => (await act(() => api.removeProjectMember(companyId, project.id, userId))) && onChanged();
  return (
    <section aria-label={`Accesos de ${project.name}`} className={styles.accesses}>
      <VisibleToAll {...props} />
      <Notice message={error} />
      <ul className={styles.list}>
        {members.map((m) => (
          <li key={m.userId} className={styles.item}>
            <span>{m.displayName}</span>
            <Button variant="danger" aria-label={`Quitar ${m.displayName}`} onClick={() => remove(m.userId)}>Quitar</Button>
          </li>
        ))}
      </ul>
      <AddMember {...props} />
    </section>
  );
}
