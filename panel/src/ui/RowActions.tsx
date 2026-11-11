import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Button, type ButtonProps } from './Button';
import { IconButton } from './IconButton';
import styles from './RowActions.module.css';

export function RowActions({ label, children }: { label: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const row = useRef<HTMLDivElement>(null);
  const keyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Escape' || !open) return;
    setOpen(false);
    row.current?.querySelector('button')?.focus();
  };
  return (
    <div ref={row} className={styles.row} onKeyDown={keyDown}>
      <IconButton icon="more" label={label} aria-expanded={open} aria-controls={panelId} onClick={() => setOpen(!open)} />
      {open && <div id={panelId} className={styles.panel}>{children}</div>}
    </div>
  );
}

type RowActionProps = Omit<ButtonProps, 'children' | 'aria-label'> & { text: string; target: string; label?: string };

/** Short visible text; the accessible name adds what it acts on («Editar Marta Ibáñez»), or `label` when the wording needs a link word. */
export function RowAction({ text, target, label = text, ...rest }: RowActionProps) {
  return <Button {...rest} aria-label={`${label} ${target}`}>{text}</Button>;
}
