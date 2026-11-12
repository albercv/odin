import type { ReactNode } from 'react';
import styles from './Field.module.css';

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <div className={styles.field}>
      <label className={styles.label}>
        {label}
        {children}
      </label>
      {error && <p role="alert" className={styles.error}>{error}</p>}
    </div>
  );
}
