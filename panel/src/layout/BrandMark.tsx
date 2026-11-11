import { useBrand } from '../theme/BrandContext';
import styles from './BrandMark.module.css';

function initialsOf(name?: string): string {
  const letters = (name ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((word) => word[0]);
  return letters.length ? letters.join('').toUpperCase() : 'O';
}

export function BrandMark({ showOwner = false }: { showOwner?: boolean }) {
  const brand = useBrand();
  return (
    <div className={styles.mark}>
      {brand.logoUrl
        ? <img className={styles.logo} src={brand.logoUrl} alt={brand.logoAlt ?? brand.name ?? 'Odín'} />
        : <span className={`${styles.logo} ${styles.initials}`} aria-hidden="true">{initialsOf(brand.name)}</span>}
      <span>
        Odín
        {showOwner && brand.name && <small className={styles.owner}>de {brand.name}</small>}
      </span>
    </div>
  );
}
