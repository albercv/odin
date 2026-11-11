import { useRef, type ReactNode } from 'react';
import { IconButton } from './IconButton';
import { useModalDialog } from './useModalDialog';
import styles from './Dialog.module.css';

type Props = { title: string; open: boolean; onClose: () => void; children: ReactNode };

const firstField = (dialog: HTMLDialogElement) => dialog.querySelector<HTMLElement>('input, select, textarea');

function Content({ title, onClose, children }: Omit<Props, 'open'>) {
  const ref = useRef<HTMLDialogElement>(null);
  useModalDialog(ref, firstField);
  return (
    <dialog ref={ref} className={styles.dialog} aria-label={title} onCancel={(event) => { event.preventDefault(); onClose(); }}>
      <div className={styles.body}>
        <div className={styles.head}>
          <h2>{title}</h2>
          <IconButton icon="close" label="Cerrar" onClick={onClose} />
        </div>
        {children}
      </div>
    </dialog>
  );
}

export function FormDialog({ open, ...rest }: Props) {
  return open ? <Content {...rest} /> : null;
}
