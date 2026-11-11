import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { Button } from './Button';
import { useModalDialog } from './useModalDialog';
import styles from './Dialog.module.css';

type Options = { message: string; confirmLabel: string; danger?: boolean };
type Confirm = (options: Options) => Promise<boolean>;
type Pending = Options & { resolve: (answer: boolean) => void };

const ConfirmContext = createContext<Confirm>(() => Promise.resolve(false));

const cancelButton = (dialog: HTMLDialogElement) => dialog.querySelector<HTMLElement>('[data-cancel]');

function Content({ message, confirmLabel, danger, resolve }: Pending) {
  const ref = useRef<HTMLDialogElement>(null);
  useModalDialog(ref, cancelButton);
  const keyDown = (event: React.KeyboardEvent) => { if (event.key === 'Escape') resolve(false); };
  return (
    <dialog ref={ref} className={styles.dialog} aria-label={message} onKeyDown={keyDown} onCancel={(event) => { event.preventDefault(); resolve(false); }}>
      <div className={styles.body}>
        <p className={styles.message}>{message}</p>
        <div className={styles.actions}>
          <Button data-cancel onClick={() => resolve(false)}>Cancelar</Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={() => resolve(true)}>{confirmLabel}</Button>
        </div>
      </div>
    </dialog>
  );
}

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<Pending | null>(null);
  const confirm = useCallback<Confirm>(
    (options) => new Promise((resolve) => setPending({ ...options, resolve })),
    [],
  );
  const settle = (answer: boolean) => { pending?.resolve(answer); setPending(null); };
  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {pending && <Content {...pending} resolve={settle} />}
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): Confirm {
  return useContext(ConfirmContext);
}
