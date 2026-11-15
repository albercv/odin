import { useRef } from 'react';
import { api, type S } from '../api/client';
import { RowAction } from '../ui/RowActions';
import { useToast } from '../ui/Toast';
import { AddPicker } from './AddPicker';
import { Notice } from './Notice';
import { useProjectTerms, type ProjectTerms } from './projectTerms';
import { useAction, useLoad } from './useLoad';
import { widelyVisible } from './widelyVisible';
import styles from './MemberAccesses.module.css';

function unassigned(all: S['ProjectView'][], mine: S['ProjectView'][]) {
  const assigned = new Set(mine.map((p) => p.id));
  const free = all.filter((p) => !assigned.has(p.id));
  return { free, wide: free.filter((p) => !p.restricted).length };
}

type AssignedProps = { listRef: React.RefObject<HTMLUListElement | null>; projects: S['ProjectView'][]; onRemove: (p: S['ProjectView']) => void };

function AssignedProjects({ listRef, projects, onRemove }: AssignedProps) {
  const t = useProjectTerms();
  return (
    <ul ref={listRef} tabIndex={-1} aria-label={`${t.Many} ${t.agree('asignados', 'asignadas')}`} className={styles.list}>
      {projects.map((p) => (
        <li key={p.id} className={styles.item}>
          <span>{p.name}</span>
          <RowAction variant="danger" text="Quitar" target={p.name} onClick={() => onRemove(p)} />
        </li>
      ))}
    </ul>
  );
}

const hint = (t: ProjectTerms, none: boolean, wide: number) =>
  [none && `Sin ${t.many} ${t.agree('asignados', 'asignadas')}.`, wide > 0 && widelyVisible(wide, 've', t)].filter(Boolean).join(' ');

type AddProps = { active: boolean; free: S['ProjectView'][]; onAdd: (id: string) => Promise<boolean> };

function AddProject({ active, free, onAdd }: AddProps) {
  const t = useProjectTerms();
  if (!active) return <p className={styles.hint}>Está desactivada: no se le pueden añadir {t.many}.</p>;
  return <AddPicker label={t.One} placeholder={`Elige ${t.aOne}`} options={free} onAdd={onAdd} />;
}

type Props = { companyId: string; member: S['MemberView']; onChanged: () => void };

/** Projects a person is a member of; the same members the project shows. */
export function MemberAccesses({ companyId, member, onChanged }: Props) {
  const mine = useLoad(() => api.memberProjects(companyId, member.userId), [companyId, member.userId]);
  const all = useLoad(() => api.projects(companyId), [companyId]);
  const list = useRef<HTMLUListElement>(null);
  const toast = useToast();
  const t = useProjectTerms();
  const { error, act } = useAction();
  const { free, wide } = unassigned(all.data ?? [], mine.data ?? []);
  const change = async (run: () => Promise<unknown>, announcement: string) => {
    const done = await act(run);
    if (!done) return false;
    mine.reload();
    onChanged();
    toast(announcement);
    return true;
  };
  const remove = async (p: S['ProjectView']) => {
    if (await change(() => api.removeProjectMember(companyId, p.id, member.userId), `Quitado ${p.name}`)) list.current?.focus();
  };
  return (
    <section aria-label={`Accesos de ${member.displayName}`} className={styles.accesses}>
      <Notice message={error ?? mine.error ?? all.error} />
      <AssignedProjects listRef={list} projects={mine.data ?? []} onRemove={remove} />
      <p className={styles.hint}>{hint(t, mine.data?.length === 0, wide)}</p>
      <AddProject active={member.active} free={free} onAdd={(id) => change(() => api.addProjectMember(companyId, id, member.userId), `Añadido ${free.find((p) => p.id === id)?.name}`)} />
    </section>
  );
}
