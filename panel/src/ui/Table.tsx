import type { ReactNode, TdHTMLAttributes, HTMLAttributes } from 'react';
import styles from './Table.module.css';

export { styles as tableStyles };

// Explicit roles: below 720 px the CSS turns the table into blocks, and some browsers then drop the table semantics.
export function Table({ columns, children }: { columns: string[]; children: ReactNode }) {
  return (
    <div className={styles.scroll}>
      <table className={styles.table} role="table">
        <thead role="rowgroup">
          <tr role="row">
            {columns.map((column) => <th key={column} role="columnheader">{column}</th>)}
            <th role="columnheader"><span className={styles.sr}>Acciones</span></th>
          </tr>
        </thead>
        <tbody role="rowgroup">{children}</tbody>
      </table>
    </div>
  );
}

export function Tr(props: HTMLAttributes<HTMLTableRowElement>) {
  return <tr role="row" {...props} />;
}

/** `label` is the column name shown next to the value on phones. */
export function Td({ label, ...rest }: TdHTMLAttributes<HTMLTableCellElement> & { label?: string }) {
  return <td role="cell" data-label={label} {...rest} />;
}
