import { useEffect, useState } from 'react';
import { activityCsvUrl, api, type ActivityFilters, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { PageHeader } from '../ui/PageHeader';
import { Table, Td, Tr } from '../ui/Table';
import { ACTIONS, CHANNELS, OUTCOMES, SYSTEM_ACTORS } from './activityLabels';
import { ActivityFooter } from './ActivityFooter';
import { ActivitySwitch } from './ActivitySwitch';
import styles from './Activity.module.css';

type Row = S['ActivityRowView'];
type Draft = Record<'from' | 'to' | 'companyId' | 'projectId' | 'action' | 'actorUserId' | 'channel' | 'outcome', string>;
const EMPTY: Draft = { from: '', to: '', companyId: '', projectId: '', action: '', actorUserId: '', channel: '', outcome: '' };

const day = (iso: string, plus = 0) => new Date(Date.parse(`${iso}T00:00:00Z`) + plus * 86_400_000).toISOString();
/** «Hasta» is inclusive for the person, the API's `to` is exclusive. */
const toFilters = ({ from, to, ...rest }: Draft): ActivityFilters => ({ ...rest, ...(from && { from: day(from) }), ...(to && { to: day(to, 1) }) });

const who = (r: Row) => SYSTEM_ACTORS[r.actorKind] ?? r.actorName ?? 'Persona suprimida';
const target = (r: Row) => r.targetLabel ?? (r.targetKind ? `${r.targetKind} ${r.targetId ?? ''}`.trim() : '');
const when = (iso: string) => new Date(iso).toLocaleString('es-ES');

function Select({ label, value, options, onChange }: { label: string; value: string; options: [string, string][]; onChange: (v: string) => void }) {
  return (
    <Field label={label}>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Todos</option>
        {options.map(([k, text]) => <option key={k} value={k}>{text}</option>)}
      </select>
    </Field>
  );
}

const pairs = (map: Record<string, string>): [string, string][] => Object.entries(map);

function Filters({ draft, set, companies }: { draft: Draft; set: (c: Partial<Draft>) => void; companies: S['CompanyRef'][] }) {
  const projects = useLoad(() => (draft.companyId ? api.projects(draft.companyId) : Promise.resolve<S['ProjectView'][]>([])), [draft.companyId]);
  return (
    <div className={styles.filters}>
      <Field label="Desde"><input type="date" value={draft.from} onChange={(e) => set({ from: e.target.value })} /></Field>
      <Field label="Hasta"><input type="date" value={draft.to} onChange={(e) => set({ to: e.target.value })} /></Field>
      <Select label="Empresa" value={draft.companyId} options={companies.map((c) => [c.id, c.name])} onChange={(companyId) => set({ companyId, projectId: '' })} />
      {draft.companyId && <Select label="Proyecto" value={draft.projectId} options={(projects.data ?? []).map((p) => [p.id, p.name])} onChange={(projectId) => set({ projectId })} />}
      <Select label="Acción" value={draft.action} options={pairs(ACTIONS)} onChange={(action) => set({ action })} />
      <Field label="Id de la persona que actúa"><input value={draft.actorUserId} onChange={(e) => set({ actorUserId: e.target.value.trim() })} /></Field>
      <Select label="Canal" value={draft.channel} options={pairs(CHANNELS)} onChange={(channel) => set({ channel })} />
      <Select label="Resultado" value={draft.outcome} options={pairs(OUTCOMES)} onChange={(outcome) => set({ outcome })} />
    </div>
  );
}

function ActivityRow({ r }: { r: Row }) {
  return (
    <Tr>
      <Td label="Fecha y hora">{when(r.occurredAt)}</Td>
      <Td label="Quién">{who(r)}</Td>
      <Td label="Papel">{r.actorRole}</Td>
      <Td label="Canal">{CHANNELS[r.channel] ?? r.channel}</Td>
      <Td label="Acción">{ACTIONS[r.action] ?? r.action}</Td>
      <Td label="Objeto">{target(r)}</Td>
      <Td label="Resultado">{OUTCOMES[r.outcome] ?? r.outcome}</Td>
      <Td label="Detalles">
        <details className={styles.details}><summary>Ver detalles</summary><pre>{JSON.stringify(r.details, null, 2)}</pre></details>
      </Td>
    </Tr>
  );
}

/** Pages follow the API cursor; a filter change starts again from the newest entry. */
function useActivity(filters: ActivityFilters) {
  const [items, setItems] = useState<Row[]>([]);
  const [cursor, setCursor] = useState<string | null>();
  const [error, setError] = useState<string>();
  const key = JSON.stringify(filters);
  const fetchPage = (from?: string) => api.activity(filters, from).then(
    (page) => { setItems((old) => (from ? [...old, ...page.items] : page.items)); setCursor(page.nextCursor); setError(undefined); },
    (e: Error) => setError(e.message));
  useEffect(() => { fetchPage(); }, [key]);
  return { items, error, more: cursor ? () => fetchPage(cursor) : undefined };
}

export function Activity({ me, companies }: { me: S['AdminMe']; companies: S['CompanyRef'][] }) {
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const filters = Object.fromEntries(Object.entries(toFilters(draft)).filter(([, v]) => v));
  const { items, error, more } = useActivity(filters);
  const integrity = useLoad(api.activityIntegrity, []);
  const isGroup = me.role === 'GROUP_ADMIN';
  return (
    <div>
      <PageHeader title="Actividad" description="Lo que se ha hecho en Odín, de lo más reciente a lo más antiguo."
        actions={<a href={activityCsvUrl(filters)} download>Exportar CSV</a>} />
      {integrity.data && !integrity.data.logEnabled && <Notice message="El registro está apagado: no se anota ninguna acción." />}
      <Notice message={error} />
      <Filters draft={draft} set={(c) => setDraft({ ...draft, ...c })} companies={companies} />
      <Table columns={['Fecha y hora', 'Quién', 'Papel', 'Canal', 'Acción', 'Objeto', 'Resultado', 'Detalles']} actions={false}>
        {items.map((r) => <ActivityRow key={r.id} r={r} />)}
      </Table>
      {more && <div className={styles.more}><Button onClick={more}>Cargar más</Button></div>}
      <ActivityFooter integrity={integrity.data} />
      {isGroup && <ActivitySwitch onChanged={integrity.reload} />}
    </div>
  );
}
