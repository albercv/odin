import { useMemo, useState } from 'react';

/** Minúsculas y sin acentos: así se comparan consulta y texto. */
export function normalizeText(s: string): string {
  return s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}

/**
 * Lista con búsqueda y páginas. `matches` recibe la consulta ya normalizada con
 * `normalizeText`; quien llama debe normalizar también el texto del elemento.
 * Con consulta vacía no se llama a `matches`. La página se acota al último
 * rango válido, así que si la lista se acorta nunca queda una página vacía.
 */
export function usePagedList<T>(items: T[], matches: (item: T, q: string) => boolean, pageSize: number) {
  const [query, setQueryState] = useState('');
  const [rawPage, setRawPage] = useState(1);
  const filtered = useMemo(() => {
    const q = normalizeText(query.trim());
    return q ? items.filter((it) => matches(it, q)) : items;
  }, [items, query, matches]);
  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const page = Math.min(rawPage, pageCount);
  const visible = filtered.slice((page - 1) * pageSize, page * pageSize);
  const setQuery = (q: string) => { setQueryState(q); setRawPage(1); };
  return { page, pageCount, visible, setQuery, setPage: setRawPage, total: filtered.length };
}
