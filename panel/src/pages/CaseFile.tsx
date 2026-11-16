import { useState } from 'react';
import { api, originalUrl, type S } from '../api/client';
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
import { Field } from '../ui/Field';
import styles from './Documents.module.css';

type Entry = S['CaseFileDocumentView'];

function EntryRow({ d, onRemove }: { d: Entry; onRemove: () => void }) {
  const [label, tone] = CASE_STATUS[d.status] ?? [d.status, 'off' as const];
  return (
    <Tr>
      <Td label="Título">
        {d.title}{d.superseded && <> <StatusPill tone="off">Sustituido</StatusPill></>}
        {d.scanOf && <div>{`Copia escaneada de ${d.scanOf.title}`}</div>}
        {d.scanCopies?.map((c) => <div key={c.documentId}>{`Copia escaneada: ${c.title}`}</div>)}
      </Td>
      <Td label="Estado"><StatusPill tone={tone}>{label}</StatusPill></Td>
      <Td label="Añadido">{d.addedAt ? formatDate(d.addedAt) : ''}</Td>
      <Td className={tableStyles.actions}><a href={originalUrl(d.id)} download aria-label={`Descargar original de ${d.title}`}>Descargar original</a> <RowAction text="Quitar" label="Quitar del expediente" target={d.title} onClick={onRemove} /></Td>
    </Tr>
  );
}

const ACTIONS: Record<string, string> = { ADDED: 'Añadido', REMOVED: 'Quitado' };

function History({ events }: { events: S['CaseFileEventView'][] }) {
  return (
    <Section title="Historial">
      {events.length === 0 && <EmptyState title="Sin cambios todavía" description="Aquí verás cuándo se añade o se quita un documento." />}
      <ul>
        {events.map((e) => <li key={e.id}>{`${ACTIONS[e.action] ?? e.action}: ${e.documentTitle ?? 'Documento retirado'} · ${formatDate(e.at)}`}</li>)}
      </ul>
    </Section>
  );
}

function CaseActions({ onAdd, onFile }: { onAdd: () => void; onFile: (file: File) => void }) {
  return (
    <div className={styles.actions}>
      <Field label="Subir al caso"><input type="file" onChange={(e) => { const f = e.target.files?.[0]; if (f) onFile(f); e.target.value = ''; }} /></Field>
      <Button variant="primary" onClick={onAdd}>Añadir documentos</Button>
    </div>
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
  const uploadHere = async (f: File) => {
    if (await act(() => api.upload(companyId, f, '', file.data?.caseDocument.projectId ?? '', false, caseId))) reload();
  };
  const documents = file.data?.documents ?? [];
  return (
    <div>
      <PageHeader title={file.data?.caseDocument.title ?? 'Expediente'} description="Documentos que forman este caso."
        actions={<CaseActions onAdd={() => setAdding(true)} onFile={uploadHere} />} />
      <Notice message={error ?? file.error} />
      <Section title="Documentos del expediente">
        {documents.length === 0 && <EmptyState title="El expediente está vacío" description="Añade documentos con «Añadir documentos»." />}
        {documents.length > 0 && <Table columns={['Título', 'Estado', 'Añadido']}>{documents.map((d) => <EntryRow key={d.id} d={d} onRemove={() => remove(d)} />)}</Table>}
      </Section>
      <History events={events.data ?? []} />
      {adding && <AddToCaseDialog companyId={companyId} caseId={caseId} projectId={file.data?.caseDocument.projectId} inFile={new Set(documents.map((d) => d.id))} onClose={() => setAdding(false)} onDone={reload} />}
    </div>
  );
}
