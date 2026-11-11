import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { RowAction } from '../ui/RowActions';
import { Field } from '../ui/Field';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './MemberAccesses.module.css';

const widelyVisible = (count: number) =>
  count === 1 ? 'Además ve 1 proyecto visible para toda la empresa.' : `Además ve los ${count} proyectos visibles para toda la empresa.`;

function AddAccess({ free, onAdd }: { free: S['ProjectView'][]; onAdd: (projectId: string) => Promise<unknown> }) {
  const [chosen, setChosen] = useState('');
  const add = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await onAdd(chosen)) setChosen('');
  };
  return (
    <form className={styles.add} onSubmit={add}>
      <Field label="Proyecto">
        <select value={chosen} onChange={(e) => setChosen(e.target.value)}>
          <option value="">Elige un proyecto</option>
          {free.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </Field>
      <Button type="submit" disabled={!chosen}>Añadir</Button>
    </form>
  );
}

/** Projects a person is a member of; the same members the project shows. */
export function MemberAccesses({ companyId, member }: { companyId: string; member: S['MemberView'] }) {
  const mine = useLoad(() => api.memberProjects(companyId, member.userId), [companyId, member.userId]);
  const all = useLoad(() => api.projects(companyId), [companyId]);
  const { error, act } = useAction();
  const assigned = new Set(mine.data?.map((p) => p.id));
  const free = all.data?.filter((p) => !assigned.has(p.id)) ?? [];
  const wide = free.filter((p) => !p.restricted).length;
  const change = async (run: () => Promise<unknown>) => {
    const done = await act(run);
    if (done) mine.reload();
    return done;
  };
  return (
    <section aria-label={`Accesos de ${member.displayName}`} className={styles.accesses}>
      <Notice message={error ?? mine.error ?? all.error} />
      <ul className={styles.list}>
        {mine.data?.map((p) => (
          <li key={p.id} className={styles.item}>
            <span>{p.name}</span>
            <RowAction variant="danger" text="Quitar" target={p.name} onClick={() => change(() => api.removeProjectMember(companyId, p.id, member.userId))} />
          </li>
        ))}
      </ul>
      {mine.data?.length === 0 && <p className={styles.hint}>Sin proyectos asignados.</p>}
      {wide > 0 && <p className={styles.hint}>{widelyVisible(wide)}</p>}
      <AddAccess free={free} onAdd={(projectId) => change(() => api.addProjectMember(companyId, projectId, member.userId))} />
    </section>
  );
}
