import { useState } from 'react';
import { intake, type S } from '../api/client';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { Notice } from './Notice';
import { useAction } from './useLoad';

/** No batch in the URL: the panel can open a new one; the command's batch is opened with its `?lote=` link. */
export function IntakeStart({ companyId, onStarted }: { companyId: string; onStarted: (batch: S['IntakeBatchView']) => void }) {
  const { error, act } = useAction();
  const [busy, setBusy] = useState(false);
  const start = async () => {
    setBusy(true);
    await act(() => intake.start(companyId).then(onStarted));
    setBusy(false);
  };
  return (
    <>
      <EmptyState title="No hay una primera carga abierta" description="Si ya subiste la carpeta con la orden, abre esta página con el enlace de su lote." />
      <p><Button variant="primary" disabled={busy} onClick={start}>Empezar una primera carga</Button></p>
      <Notice message={error} />
    </>
  );
}
