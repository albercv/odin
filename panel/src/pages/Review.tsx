import { useEffect, useRef, useState } from 'react';
import { api, type S } from '../api/client';
import { CardDetail } from '../components/CardDetail';
import { Notice } from '../components/Notice';
import { useLoad } from '../components/useLoad';
import { useServerPage } from '../components/useServerPage';
import { Pagination } from '../ui/Pagination';
import { SearchBox } from '../ui/SearchBox';
import { EmptyState } from '../ui/EmptyState';
import { PageHeader } from '../ui/PageHeader';
import { authorName, formatDate, projectName } from './reviewNames';
import styles from './Review.module.css';

const LEAVE_MS = 240;
const PAGE_SIZE = 10;

type Names = { members: S['MemberView'][]; projects: S['ProjectView'][] };

type QueueProps = { cards: S['CardView'][]; names: Names; selectedId?: string; leavingId?: string; paging: Paging; onSelect: (id: string) => void };

function Queue({ cards, names, selectedId, leavingId, paging, onSelect }: QueueProps) {
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
      <Pagination label="Páginas de fichas" {...paging} />
    </section>
  );
}

type Paging = { page: number; pageCount: number; onPage: (p: number) => void };

function useResolvedCards(items: S['CardView'][], reload: () => void) {
  const [leaving, setLeaving] = useState<{ id: string; items: S['CardView'][] }>();
  // La ficha resuelta sigue a la vista hasta que llega la página recargada (otra lista).
  const leavingId = leaving?.items === items ? leaving.id : undefined;
  const [selected, setSelected] = useState<string>();
  const current = items.find((c) => c.id === selected) ?? items.find((c) => c.id !== leavingId) ?? items[0];
  const resolve = (id: string) => {
    const index = items.findIndex((c) => c.id === id);
    setSelected((items[index + 1] ?? items[index - 1])?.id);
    setLeaving({ id, items });
    setTimeout(reload, LEAVE_MS);
  };
  return { current, leavingId, setSelected, resolve };
}

/** El número del menú es el total sin filtrar del servidor: con una búsqueda activa, tras resolver una ficha se le vuelve a pedir. */
function useBadge(companyId: string, list: { total: number; ready: boolean; searching: boolean }, onPendingChanged?: (count: number) => void) {
  const { total, ready, searching } = list;
  useEffect(() => { if (ready && !searching) onPendingChanged?.(total); }, [total, ready, searching]);
  return () => { if (searching) api.pendingCount(companyId).then((n) => onPendingChanged?.(n), () => undefined); };
}

function useReviewList(companyId: string, onPendingChanged?: (count: number) => void) {
  const list = useServerPage<S['CardView']>(async (q, offset, limit) => {
    const r = await api.cardPage(companyId, { q, offset, limit });
    return { items: r.cards, total: r.total };
  }, [companyId], PAGE_SIZE);
  const resolvedInSearch = useBadge(companyId, { total: list.total, ready: !list.loading && !list.error, searching: !!list.query }, onPendingChanged);
  return { list, resolvedInSearch };
}

function useNames(companyId: string) {
  return { members: useLoad(() => api.members(companyId), [companyId]), projects: useLoad(() => api.projects(companyId), [companyId]) };
}

type Props = { companyId: string; onPendingChanged?: (count: number) => void };

export function Review({ companyId, onPendingChanged }: Props) {
  const { list, resolvedInSearch } = useReviewList(companyId, onPendingChanged);
  const { members, projects } = useNames(companyId);
  const { current, leavingId, setSelected, resolve } = useResolvedCards(list.items, list.reload);
  const [resolved, setResolved] = useState(false);
  const emptyHeading = useRef<HTMLHeadingElement>(null);
  const empty = !list.loading && !list.error && !list.query && list.total === 0;
  useEffect(() => { if (resolved && empty) emptyHeading.current?.focus(); }, [resolved, empty]);
  const names = { members: members.data ?? [], projects: projects.data ?? [] };
  const paging = { page: list.page, pageCount: list.pageCount, onPage: list.setPage };
  return (
    <div>
      <PageHeader title="Revisión de fichas"
        description="Las fichas que la gente guarda desde su IA esperan aquí. Cuando apruebas una, la IA de la empresa ya puede citarla." />
      <Notice message={list.error ?? members.error ?? projects.error} />
      <SearchBox label="Buscar fichas" onSearch={list.setQuery} resultCount={list.loading ? undefined : list.total} />
      {empty && <EmptyState headingRef={emptyHeading} title="No hay fichas pendientes." description="Las nuevas aparecerán aquí cuando alguien guarde una desde su IA." />}
      {members.data && projects.data && current && (
        <div className={styles.review}>
          <Queue cards={list.items} names={names} selectedId={current.id} leavingId={leavingId} paging={paging} onSelect={(id) => { setResolved(false); setSelected(id); }} />
          <section className={styles.panel} aria-label="Ficha seleccionada" aria-live="polite">
            <CardDetail key={current.id} card={current} author={authorName(current.authorId, names.members)}
              project={projectName(current.projectId, names.projects)} locked={leavingId !== undefined} focusTitle={resolved}
              onResolved={() => { setResolved(true); resolvedInSearch(); resolve(current.id); }} />
          </section>
        </div>
      )}
    </div>
  );
}
