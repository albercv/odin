import { Button } from './Button';
import styles from './Pagination.module.css';

// aria-disabled (no disabled) para que el botón conserve el foco al llegar al borde.
function Step({ disabled, onClick, children }: { disabled: boolean; onClick: () => void; children: string }) {
  return (
    <Button variant="ghost" aria-disabled={disabled || undefined} className={styles.step}
      onClick={() => { if (!disabled) onClick(); }}>
      {children}
    </Button>
  );
}

export function Pagination({ label, page, pageCount, onPage }: {
  label: string; page: number; pageCount: number; onPage: (page: number) => void;
}) {
  if (pageCount <= 1) return null;
  return (
    <nav aria-label={label} className={styles.nav}>
      <Step disabled={page <= 1} onClick={() => onPage(page - 1)}>Anterior</Step>
      <span>Página {page} de {pageCount}</span>
      <Step disabled={page >= pageCount} onClick={() => onPage(page + 1)}>Siguiente</Step>
    </nav>
  );
}
