import { useEffect, useRef, useState } from 'react';

type Fetch<T> = (q: string, offset: number, limit: number) => Promise<{ items: T[]; total: number }>;

type Data<T> = { items: T[]; total: number };

type Settled<T> = { key: string; data: Data<T>; error?: string };

/** Pide la página y descarta las respuestas que ya no son la última petición. `loading` es cierto mientras el resultado guardado no sea el de la petición actual. */
function useFetched<T>(fetchPage: Fetch<T>, query: string, page: number, size: number, extra: unknown[], onPast: (last: number) => void) {
  const [settled, setSettled] = useState<Settled<T>>();
  const key = JSON.stringify([query, page, size, ...extra]);
  const latest = useRef({ fetchPage, onPast });
  latest.current = { fetchPage, onPast };
  const request = useRef(0);
  useEffect(() => {
    const id = ++request.current;
    latest.current.fetchPage(query, (page - 1) * size, size).then((r) => {
      if (id !== request.current) return;
      setSettled({ key, data: r });
      const last = Math.max(1, Math.ceil(r.total / size));
      if (page > last) latest.current.onPast(last);
    }, (e: Error) => { if (id === request.current) setSettled({ key, data: { items: [], total: 0 }, error: e.message }); });
  }, [key]);
  const current = settled?.key === key;
  return { data: settled?.data ?? { items: [], total: 0 }, loading: !current, error: current ? settled.error : undefined };
}

/**
 * Lista paginada en el servidor. Vuelve a la página 1 al cambiar búsqueda, tamaño o `deps`;
 * `reload` conserva la página (o va a la última si ya no existe).
 */
export function useServerPage<T>(fetchPage: Fetch<T>, deps: unknown[], initialSize = 25) {
  const [query, setQueryState] = useState('');
  const [pageSize, setSize] = useState(initialSize);
  const [at, setAt] = useState({ page: 1, key: '' });
  const [tick, setTick] = useState(0);
  const depsKey = JSON.stringify(deps);
  if (at.key !== depsKey) setAt({ page: 1, key: depsKey });
  const page = at.key === depsKey ? at.page : 1;
  const setPage = (p: number) => setAt({ page: p, key: depsKey });
  const { data, loading, error } = useFetched(fetchPage, query, page, pageSize, [depsKey, tick], setPage);
  return {
    query, items: data.items, total: data.total, page, pageSize, loading, error,
    pageCount: Math.max(1, Math.ceil(data.total / pageSize)),
    setPage, reload: () => setTick((t) => t + 1),
    setQuery: (q: string) => { setQueryState(q); setPage(1); },
    setPageSize: (n: number) => { setSize(n); setPage(1); },
  };
}
