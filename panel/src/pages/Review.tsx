import { useEffect, useState } from 'react';
import { api, type S } from '../api/client';
import { CardDetail } from '../components/CardDetail';
import { Notice } from '../components/Notice';
import { useLoad } from '../components/useLoad';
import { EmptyState } from '../ui/EmptyState';
import { PageHeader } from '../ui/PageHeader';
import { authorName, formatDate, projectName } from './reviewNames';
import styles from './Review.module.css';

const LEAVE_MS = 240;

type Names = { members: S['MemberView'][]; projects: S['ProjectView'][] };

type QueueProps = { cards: S['CardView'][]; names: Names; selectedId?: string; leavingId?: string; onSelect: (id: string) => void };

function Queue({ cards, names, selectedId, leavingId, onSelect }: QueueProps) {
  return (
    <section className={styles.panel} aria-label="Fichas pendientes">
      <ul className={styles.queue}>
        {cards.map((c) => (
          <li key={c.id} className={c.id === leavingId ? styles.leaving : undefined}>
            <button type="button" aria-current={c.id === selectedId} onClick={() => onSelect(c.id)}>
              <strong>{c.title}</strong>
              <span>{projectName(c.projectId, names.projects)}</span>
              <span>{authorName(c.authorId, names.members)}, {formatDate(c.createdAt)}</span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function useResolvedCards(cards: S['CardView'][] | undefined, onPendingChanged?: (count: number) => void) {
  const [gone, setGone] = useState<string[]>([]);
  const [leavingId, setLeavingId] = useState<string>();
  const [selected, setSelected] = useState<string>();
  const visible = (cards ?? []).filter((c) => !gone.includes(c.id));
  const current = visible.find((c) => c.id === selected) ?? visible.find((c) => c.id !== leavingId) ?? visible[0];
  useEffect(() => { if (cards) onPendingChanged?.(visible.length); }, [cards, visible.length]);
  const resolve = (id: string) => {
    const index = visible.findIndex((c) => c.id === id);
    setSelected((visible[index + 1] ?? visible[index - 1])?.id);
    setLeavingId(id);
    setTimeout(() => {
      setGone((ids) => [...ids, id]);
      setLeavingId(undefined);
    }, LEAVE_MS);
  };
  return { visible, current, leavingId, setSelected, resolve };
}

export function Review({ companyId, onPendingChanged }: { companyId: string; onPendingChanged?: (count: number) => void }) {
  const list = useLoad(() => api.pendingCards(companyId), [companyId]);
  const members = useLoad(() => api.members(companyId), [companyId]);
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const { visible, current, leavingId, setSelected, resolve } = useResolvedCards(list.data, onPendingChanged);
  const names = { members: members.data ?? [], projects: projects.data ?? [] };
  const loaded = list.data && members.data && projects.data;
  return (
    <div>
      <PageHeader title="Revisión de fichas"
        description="Las fichas que la gente guarda desde su IA esperan aquí. Cuando apruebas una, la IA de la empresa ya puede citarla." />
      <Notice message={list.error ?? members.error ?? projects.error} />
      {visible.length === 0 && list.data && <EmptyState title="No hay fichas pendientes." description="Las nuevas aparecerán aquí cuando alguien guarde una desde su IA." />}
      {loaded && visible.length > 0 && current && (
        <div className={styles.review}>
          <Queue cards={visible} names={names} selectedId={current.id} leavingId={leavingId} onSelect={setSelected} />
          <section className={styles.panel} aria-label="Ficha seleccionada" aria-live="polite">
            <CardDetail key={current.id} card={current} author={authorName(current.authorId, names.members)}
              project={projectName(current.projectId, names.projects)} locked={leavingId !== undefined} onResolved={() => resolve(current.id)} />
          </section>
        </div>
      )}
    </div>
  );
}
