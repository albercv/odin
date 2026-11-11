import type { ReactNode } from 'react';
import styles from './StatusPill.module.css';

export function StatusPill({ tone, children }: { tone: 'ok' | 'off' | 'pending'; children: ReactNode }) {
  return <span className={`${styles.pill} ${styles[tone]}`}>{children}</span>;
}
