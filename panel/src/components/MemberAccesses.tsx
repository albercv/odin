import { useRef } from 'react';
import { api, type S } from '../api/client';
import { RowAction } from '../ui/RowActions';
import { useToast } from '../ui/Toast';
import { AddPicker } from './AddPicker';
import { Notice } from './Notice';
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
  return (
    <ul ref={listRef} tabIndex={-1} aria-label="Proyectos asignados" className={styles.list}>
      {projects.map((p) => (
        <li key={p.id} className={styles.item}>
          <span>{p.name}</span>
          <RowAction variant="danger" text="Quitar" target={p.name} onClick={() => onRemove(p)} />
        </li>
      ))}
    </ul>
  );
}

type Props = { companyId: string; member: S['MemberView']; onChanged: () => void };

/** Projects a person is a member of; the same members the project shows. */
export function MemberAccesses({ companyId, member, onChanged }: Props) {
  const mine = useLoad(() => api.memberProjects(companyId, member.userId), [companyId, member.userId]);
  const all = useLoad(() => api.projects(companyId), [companyId]);
  const list = useRef<HTMLUListElement>(null);
  const toast = useToast();
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
      <p className={styles.hint}>{[mine.data?.length === 0 && 'Sin proyectos asignados.', wide > 0 && widelyVisible(wide, 've')].filter(Boolean).join(' ')}</p>
      {member.active
        ? <AddPicker label="Proyecto" placeholder="Elige un proyecto" options={free}
          onAdd={(id) => change(() => api.addProjectMember(companyId, id, member.userId), `Añadido ${free.find((p) => p.id === id)?.name}`)} />
        : <p className={styles.hint}>Está desactivada: no se le pueden añadir proyectos.</p>}
    </section>
  );
}
