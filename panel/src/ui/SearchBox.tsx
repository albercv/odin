import { useEffect, useRef, useState } from 'react';
import styles from './SearchBox.module.css';

function countText(n: number, q: string) {
  if (n === 0) return `Ningún resultado para «${q}»`;
  return n === 1 ? '1 resultado' : `${n} resultados`;
}

function useEmitter(onSearch: (q: string) => void) {
  const [active, setActive] = useState('');
  const last = useRef('');
  const latest = useRef(onSearch);
  latest.current = onSearch;
  const emit = (q: string) => {
    if (q === last.current) return;
    last.current = q;
    setActive(q);
    latest.current(q);
  };
  return { active, emit };
}

/** Entrega a `onSearch` el texto (si llega a `minLength`) o «» tras `delayMs`; Escape borra al instante. */
export function SearchBox({ label, onSearch, resultCount, minLength = 3, delayMs = 300 }: {
  label: string; onSearch: (q: string) => void; resultCount?: number; minLength?: number; delayMs?: number;
}) {
  const [text, setText] = useState('');
  const { active, emit } = useEmitter(onSearch);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);

  const change = (value: string) => {
    setText(value);
    clearTimeout(timer.current);
    const q = value.trim();
    timer.current = setTimeout(() => emit(q.length >= minLength ? q : ''), delayMs);
  };
  const clear = () => { clearTimeout(timer.current); setText(''); emit(''); };

  return (
    <div role="search" className={styles.search}>
      <input
        type="search" aria-label={label} placeholder={label} value={text}
        onChange={(e) => change(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Escape' && text) { e.preventDefault(); e.stopPropagation(); clear(); } }}
      />
      <p aria-live="polite" className={styles.count}>
        {active && resultCount !== undefined ? countText(resultCount, active) : ''}
      </p>
    </div>
  );
}
