import { useRef, type ReactNode } from 'react';
import { IconButton } from './IconButton';
import { useModalDialog } from './useModalDialog';
import styles from './Dialog.module.css';

type Props = { title: string; open: boolean; onClose: () => void; children: ReactNode; dismissible?: boolean };

const firstField = (dialog: HTMLDialogElement) => dialog.querySelector<HTMLElement>('input, select, textarea');

function Content({ title, onClose, children, dismissible = true }: Omit<Props, 'open'>) {
  const ref = useRef<HTMLDialogElement>(null);
  useModalDialog(ref, firstField);
  return (
    <dialog ref={ref} className={styles.dialog} aria-label={title} onCancel={(event) => { event.preventDefault(); if (dismissible) onClose(); }}
      onKeyDown={(event) => { if (event.key === 'Escape' && !dismissible) event.preventDefault(); }}
      onClose={() => { if (!dismissible && ref.current && !ref.current.open) ref.current.showModal(); }}>
      <div className={styles.body}>
        <div className={styles.head}>
          <h2>{title}</h2>
          {dismissible && <IconButton icon="close" label="Cerrar" onClick={onClose} />}
        </div>
        {children}
      </div>
    </dialog>
  );
}

export function FormDialog({ open, ...rest }: Props) {
  return open ? <Content {...rest} /> : null;
}
