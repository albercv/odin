import { intake, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Notice } from './Notice';
import { useAction } from './useLoad';
import styles from './Intake.module.css';

const MESSAGE = 'Cerrar la carga. Lo que está en "No lo sé" pasa a "Sin ubicar" y la carga deja de estar en revisión. Lo que quede sin ubicar seguirá oculto hasta que lo coloques o lo descartes.';

/** The admin closes the load: what is still "No lo sé" becomes "Sin ubicar". Not while proposals are undecided. */
export function IntakeClose({ companyId, batch, onClosed }: { companyId: string; batch: S['IntakeBatchView']; onClosed: () => void }) {
  const { error, act } = useAction();
  const confirm = useConfirm();
  const toDecide = batch.toDecide;
  const close = async () => {
    if (await confirm({ message: MESSAGE, confirmLabel: 'Cerrar la carga' }) && await act(() => intake.close(companyId, batch.id))) onClosed();
  };
  return (
    <div className={styles.tools}>
      <Notice message={error} />
      <Button variant="primary" disabled={toDecide > 0} onClick={close}>Cerrar la carga</Button>
      {toDecide > 0 && <span className={styles.reason}>{toDecide === 1 ? 'Queda 1 propuesta por decidir' : `Quedan ${toDecide} propuestas por decidir`}</span>}
    </div>
  );
}
