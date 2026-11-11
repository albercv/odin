import { useEffect, type RefObject } from 'react';

/** Opens the dialog as modal, moves focus into it and gives it back to the opener on unmount. */
export function useModalDialog(ref: RefObject<HTMLDialogElement | null>, initialFocus: (dialog: HTMLDialogElement) => HTMLElement | null): void {
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return undefined;
    const opener = document.activeElement as HTMLElement | null;
    dialog.showModal();
    initialFocus(dialog)?.focus();
    return () => {
      dialog.close();
      opener?.focus();
    };
  }, [ref, initialFocus]);
}
