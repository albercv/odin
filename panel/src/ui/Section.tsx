import { useId, type ReactNode } from 'react';
import styles from './Section.module.css';

type Props = { title: string; description?: string; actions?: ReactNode; children?: ReactNode };

export function Section({ title, description, actions, children }: Props) {
  const headingId = useId();
  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <div className={styles.head}>
        <div>
          <h2 id={headingId}>{title}</h2>
          {description && <p className={styles.description}>{description}</p>}
        </div>
        {actions}
      </div>
      {children}
    </section>
  );
}
