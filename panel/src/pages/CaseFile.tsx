import { useState } from 'react';
import { api, type S } from '../api/client';
import { AddToCaseDialog } from '../components/AddToCaseDialog';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';
import { useConfirm } from '../ui/ConfirmDialog';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { PageHeader } from '../ui/PageHeader';
import { RowAction } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, Td, Tr, tableStyles } from '../ui/Table';
import { CASE_STATUS } from './documentStatus';
import { formatDate } from './reviewNames';

type Entry = S['CaseFileDocumentView'];

function EntryRow({ d, onRemove }: { d: Entry; onRemove: () => void }) {
  const [label, tone] = CASE_STATUS[d.status] ?? [d.status, 'off' as const];
  return (
    <Tr>
      <Td label="Título">{d.title}{d.superseded && <> <StatusPill tone="off">Sustituido</StatusPill></>}</Td>
      <Td label="Estado"><StatusPill tone={tone}>{label}</StatusPill></Td>
      <Td label="Añadido">{d.addedAt ? formatDate(d.addedAt) : ''}</Td>
      <Td className={tableStyles.actions}><RowAction text="Quitar" label="Quitar del expediente" target={d.title} onClick={onRemove} /></Td>
    </Tr>
  );
}

const ACTIONS: Record<string, string> = { ADDED: 'Añadido', REMOVED: 'Quitado' };

function History({ events, titles }: { events: S['CaseFileEventView'][]; titles: Map<string, string> }) {
  return (
    <Section title="Historial">
      {events.length === 0 && <EmptyState title="Sin cambios todavía" description="Aquí verás cuándo se añade o se quita un documento." />}
      <ul>
        {events.map((e) => <li key={e.id}>{`${ACTIONS[e.action] ?? e.action}: ${titles.get(e.documentId) ?? 'un documento'} · ${formatDate(e.at)}`}</li>)}
      </ul>
    </Section>
  );
}

export function CaseFile({ companyId, caseId }: { companyId: string; caseId: string }) {
  const file = useLoad(() => api.caseFile(companyId, caseId), [companyId, caseId]);
  const events = useLoad(() => api.caseEvents(companyId, caseId), [companyId, caseId]);
  const [adding, setAdding] = useState(false);
  const confirm = useConfirm();
  const { error, act } = useAction();
  const reload = () => { file.reload(); events.reload(); };
  const remove = async (d: Entry) => {
    const message = `¿Quitar «${d.title}» del expediente? El documento sigue en Odín; solo deja de formar parte de este caso.`;
    if (await confirm({ message, confirmLabel: 'Quitar', danger: true }) && await act(() => api.removeFromCase(companyId, caseId, d.id))) reload();
  };
  const documents = file.data?.documents ?? [];
  const titles = new Map(documents.map((d) => [d.id, d.title]));
  return (
    <div>
      <PageHeader title={file.data?.caseDocument.title ?? 'Expediente'} description="Documentos que forman este caso."
        actions={<Button variant="primary" onClick={() => setAdding(true)}>Añadir documentos</Button>} />
      <Notice message={error ?? file.error} />
      <Section title="Documentos del expediente">
        {documents.length === 0 && <EmptyState title="El expediente está vacío" description="Añade documentos con «Añadir documentos»." />}
        {documents.length > 0 && <Table columns={['Título', 'Estado', 'Añadido']}>{documents.map((d) => <EntryRow key={d.id} d={d} onRemove={() => remove(d)} />)}</Table>}
      </Section>
      <History events={events.data ?? []} titles={titles} />
      {adding && <AddToCaseDialog companyId={companyId} caseId={caseId} projectId={file.data?.caseDocument.projectId} inFile={new Set(documents.map((d) => d.id))} onClose={() => setAdding(false)} onDone={reload} />}
    </div>
  );
}
