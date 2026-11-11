import type { ReactNode } from 'react';
import styles from './Table.module.css';

export { styles as tableStyles };

export function Table({ columns, children }: { columns: string[]; children: ReactNode }) {
  return (
    <div className={styles.scroll}>
      <table className={styles.table}>
        <thead>
          <tr>
            {columns.map((column) => <th key={column}>{column}</th>)}
            <th><span className={styles.sr}>Acciones</span></th>
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
