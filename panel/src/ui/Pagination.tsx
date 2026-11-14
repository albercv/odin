import { Button } from './Button';
import styles from './Pagination.module.css';

export function Pagination({ label, page, pageCount, onPage }: {
  label: string; page: number; pageCount: number; onPage: (page: number) => void;
}) {
  if (pageCount <= 1) return null;
  return (
    <nav aria-label={label} className={styles.nav}>
      <Button variant="ghost" disabled={page <= 1} onClick={() => onPage(page - 1)}>Anterior</Button>
      <span>Página {page} de {pageCount}</span>
      <Button variant="ghost" disabled={page >= pageCount} onClick={() => onPage(page + 1)}>Siguiente</Button>
    </nav>
  );
}
