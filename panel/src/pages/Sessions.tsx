import { api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { EmptyState } from '../ui/EmptyState';
import { PageHeader } from '../ui/PageHeader';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, Td, Tr } from '../ui/Table';

type Row = S['AdminSessionView'];

const when = (iso: string) => new Date(iso).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' });

function SessionRow({ s, onClose }: { s: Row; onClose: (s: Row) => void }) {
  return (
    <Tr>
      <Td label="Desde">{when(s.createdAt)}</Td>
      <Td label="Último uso">{when(s.lastSeenAt)}</Td>
      <Td label="Navegador">{s.userAgent ?? '—'}</Td>
      <Td label="IP">{s.ip ?? '—'}</Td>
      <Td>{s.current && <StatusPill tone="ok">Esta sesión</StatusPill>}</Td>
      <Td><Button onClick={() => onClose(s)}>Cerrar</Button></Td>
    </Tr>
  );
}

export function Sessions() {
  const { data, error, reload } = useLoad(() => api.mySessions(), []);
  const { error: closeError, act } = useAction();
  const confirm = useConfirm();
  const close = async (s: Row) => {
    const message = s.current ? '¿Cerrar esta sesión? Volverás a la pantalla de entrada.' : '¿Cerrar esta sesión? Quien la use tendrá que volver a entrar.';
    if (await confirm({ message, confirmLabel: 'Cerrar', danger: true }) && await act(() => api.closeSession(s.id))) reload();
  };
  return (
    <div>
      <PageHeader title="Mis sesiones" description="Los equipos donde has entrado en el panel. Solo ves y cierras las tuyas." />
      <Notice message={error ?? closeError} />
      <Section title="Sesiones abiertas">
        {data?.length === 0 && <EmptyState title="No hay sesiones abiertas" />}
        {!!data?.length && (
          <Table columns={['Desde', 'Último uso', 'Navegador', 'IP', 'Sesión']}>
            {data.map((s) => <SessionRow key={s.id} s={s} onClose={close} />)}
          </Table>
        )}
      </Section>
    </div>
  );
}
