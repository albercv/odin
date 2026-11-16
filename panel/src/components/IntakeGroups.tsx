import { useState, type ReactNode } from 'react';
import { intake, intakeOriginalUrl, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { EmptyState } from '../ui/EmptyState';
import { FormDialog } from '../ui/FormDialog';
import { Section } from '../ui/Section';
import type { Step } from './IntakeCandidates';
import { IntakeProposalRow, type Decision } from './IntakeProposalRow';
import { Notice } from './Notice';
import { useProjectTerms, type ProjectTerms } from './projectTerms';
import { useAction, useLoad } from './useLoad';
import styles from './Intake.module.css';

type Group = S['IntakeProposalGroup'];
type Run = (fn: () => Promise<unknown>) => void;

const KIND: Record<string, string> = {
  COMPANY_WIDE: 'De toda la empresa', SAME_AS_SCAN: 'Escaneos y su copia', SUPERSEDES: 'Versiones',
  CASE_MARK: 'Documentos de caso', CASE_LINK: 'Enlaces a fichas de caso',
};
const noun = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
const EFFECT: Record<string, (n: number, project: string, t: ProjectTerms) => string> = {
  PROJECT: (n, project, t) => `¿Confirmar ${noun(n, 'documento', 'documentos')} en ${project}? Desde ese momento los verán quienes ven ${t.the} ${t.one}.`,
  COMPANY_WIDE: (n) => `¿Confirmar ${noun(n, 'documento', 'documentos')} como de toda la empresa? Desde ese momento los verá toda la empresa.`,
  SAME_AS_SCAN: (n) => `¿Confirmar ${noun(n, 'pareja', 'parejas')} de escaneo y copia? Cada escaneo quedará enlazado con su copia.`,
  SUPERSEDES: (n) => `¿Confirmar ${noun(n, 'versión', 'versiones')}? Cada una sustituirá a la anterior.`,
  CASE_MARK: (n) => `¿Marcar ${noun(n, 'documento', 'documentos')} como documentos de caso?`,
  CASE_LINK: (n) => `¿Confirmar ${noun(n, 'documento', 'documentos')} en sus fichas de caso? Se añadirán a la ficha con el número que citan.`,
};

const groupLabel = (g: Group, projects: S['ProjectView'][], t: ProjectTerms) => {
  const project = projects.find((p) => p.id === g.projectId)?.name;
  if (g.kind === 'PROJECT') return project ?? `${t.One} sin nombre`;
  return project ? `${KIND[g.kind] ?? g.kind} (${project})` : KIND[g.kind] ?? g.kind;
};

/** The API takes at most 500 proposals per request (each one all or nothing): bigger groups go in parts. */
const PART = 500;
const PARTS_NOTE = ` Va en partes de hasta ${PART} propuestas y cada parte es todo o nada.`;

async function confirmInParts(ids: Ids, proposalIds: string[]) {
  const parts = Array.from({ length: Math.ceil(proposalIds.length / PART) }, (_, i) => proposalIds.slice(i * PART, (i + 1) * PART));
  for (const [i, part] of parts.entries()) {
    try {
      await intake.confirmProposals(ids.company, ids.batch, part);
    } catch (e) {
      if (parts.length === 1) throw e;
      throw new Error(`Se aplicaron ${i} de ${parts.length} partes (${i * PART} propuestas); la siguiente falló: ${(e as Error).message}`, { cause: e });
    }
  }
}

// a HIGH group in a project missing from the list cannot be confirmed yet (same rule as GroupCard)
const isUnknownProject = (g: Group, projects: S['ProjectView'][]) => g.kind === 'PROJECT' && !projects.some((p) => p.id === g.projectId);

type Ids = { company: string; batch: string };
type Outcome = { text: string; failed: boolean };

type SafeProps = { groups: Group[]; skipped: Group[]; labelOf: (g: Group) => string; ids: Ids };

/** Confirms the groups in order and stops at the first failure; what was confirmed stays. */
async function confirmGroups({ groups, labelOf, ids }: SafeProps, onProgress: (n: number) => void): Promise<Outcome> {
  let confirmed = 0;
  for (const [i, g] of groups.entries()) {
    onProgress(i + 1);
    try {
      await confirmInParts(ids, g.proposals.map((p) => p.id));
    } catch (e) {
      return { failed: true, text: `Se confirmaron ${noun(i, 'grupo', 'grupos')} de ${groups.length} (${noun(confirmed, 'propuesta', 'propuestas')}). Falló «${labelOf(g)}»: ${(e as Error).message}` };
    }
    confirmed += g.proposals.length;
  }
  return { failed: false, text: `Se confirmaron ${noun(groups.length, 'grupo', 'grupos')} (${noun(confirmed, 'propuesta', 'propuestas')}).` };
}

type SummaryProps = SafeProps & { close: () => void; start: () => void };

function Summary({ groups, skipped, labelOf, close, start }: SummaryProps) {
  const docs = groups.reduce((n, g) => n + g.proposals.length, 0);
  return (
    <>
      <p>{`Se van a confirmar ${noun(groups.length, 'grupo seguro', 'grupos seguros')}, con ${noun(docs, 'documento', 'documentos')} en total.`}</p>
      <ul className={styles.summaryList}>{groups.map((g) => <li key={`${g.kind}-${g.projectId}`}>{`${labelOf(g)} · ${noun(g.proposals.length, 'documento', 'documentos')}`}</li>)}</ul>
      {skipped.length > 0 && <p>{`${noun(skipped.length, 'grupo seguro no se puede confirmar todavía', 'grupos seguros no se pueden confirmar todavía')}: su proyecto no está en la lista de proyectos.`}</p>}
      <p>Las propuestas dudosas no se tocan: siguen una a una.</p>
      <div className={styles.tools}>
        <Button onClick={close}>Cancelar</Button>
        <Button variant="primary" onClick={start}>{`Confirmar ${noun(groups.length, 'grupo', 'grupos')}`}</Button>
      </div>
    </>
  );
}

/** A button, a summary dialog in front (D2: nothing is confirmed without that click), then the progress and the result. */
function ConfirmSafe(props: SafeProps & { onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [progress, setProgress] = useState<number>();
  const [outcome, setOutcome] = useState<Outcome>();
  const close = () => { setOpen(false); setOutcome(undefined); };
  const start = async () => {
    setOutcome(await confirmGroups(props, setProgress));
    setProgress(undefined);
    props.onDone();
  };
  return (
    <>
      <Button variant="primary" onClick={() => setOpen(true)}>Confirmar todos los grupos seguros</Button>
      <FormDialog open={open} title="Confirmar todos los grupos seguros" onClose={close} dismissible={progress === undefined}>
        {outcome && <p role={outcome.failed ? 'alert' : 'status'}>{outcome.text}</p>}
        {outcome && <div className={styles.tools}><Button onClick={close}>Cerrar</Button></div>}
        {!outcome && progress !== undefined && <p role="status">{`Confirmando grupo ${progress} de ${props.groups.length}…`}</p>}
        {!outcome && progress === undefined && <Summary {...props} close={close} start={start} />}
      </FormDialog>
    </>
  );
}

type GroupProps = { group: Group; label: string; projects: S['ProjectView'][]; run: Run; ids: Ids };

function GroupCard({ group, label, projects, run, ids }: GroupProps) {
  const [open, setOpen] = useState(false);
  const confirm = useConfirm();
  const t = useProjectTerms();
  const count = group.proposals.length;
  // never ask to confirm documents in a project whose name is not known (D2: informed consent)
  const unknownProject = isUnknownProject(group, projects);
  const confirmGroup = async () => {
    const message = (EFFECT[group.kind]?.(count, label, t) ?? `¿Confirmar ${noun(count, 'documento', 'documentos')}?`) + (count > PART ? PARTS_NOTE : '');
    if (await confirm({ message, confirmLabel: 'Confirmar' })) run(() => confirmInParts(ids, group.proposals.map((p) => p.id)));
  };
  const decide = (id: string, d: Decision) => run(() => intake.decide(ids.company, ids.batch, id, d));
  return (
    <div className={styles.group}>
      <div className={styles.groupHead}>
        <h3>{`${label} · confianza ${group.confidence === 'HIGH' ? 'alta' : 'baja'} · ${noun(count, 'documento', 'documentos')}`}</h3>
        <div className={styles.tools}>
          <Button aria-expanded={open} aria-label={`Ver documentos de ${label}`} onClick={() => setOpen(!open)}>{open ? 'Ocultar' : 'Ver documentos'}</Button>
          <Button variant="primary" aria-label={`Confirmar grupo ${label}`} disabled={unknownProject} onClick={confirmGroup}>Confirmar grupo</Button>
        </div>
      </div>
      {open && (
        <ul className={styles.items}>
          {group.proposals.map((p) => <IntakeProposalRow key={p.id} kind={group.kind} item={p} projectKind={group.kind === 'PROJECT' || group.kind === 'COMPANY_WIDE'} projects={projects} decide={decide} original={intakeOriginalUrl(ids.company, ids.batch, p.documentId)} />)}
        </ul>
      )}
    </div>
  );
}

type Props = Step & { kinds: string[]; title: string; description: string; empty: string; children?: ReactNode };

/** Proposals of some kinds, grouped by the API. Nothing changes until a person confirms, one by one or a whole group. */
export function IntakeGroups({ companyId, batchId, projects, onChanged, kinds, title, description, empty, children }: Props) {
  const list = useLoad(() => intake.proposals(companyId, batchId), [companyId, batchId]);
  const { error, act } = useAction();
  const t = useProjectTerms();
  // refresh also after a failure: part of a group may have been confirmed
  const run: Run = async (fn) => { await act(fn); list.reload(); onChanged(); };
  const groups = (list.data?.groups ?? []).filter((g) => kinds.includes(g.kind));
  const high = groups.filter((g) => g.confidence === 'HIGH');
  const safe = high.filter((g) => !isUnknownProject(g, projects));
  const ids = { company: companyId, batch: batchId };
  return (
    <>
      <Section title={title} description={description}>
        <div className={styles.body}>
          <Notice message={list.error ?? error} />
          {safe.length > 0 && <ConfirmSafe groups={safe} skipped={high.filter((g) => isUnknownProject(g, projects))} labelOf={(g) => groupLabel(g, projects, t)} ids={ids} onDone={() => { list.reload(); onChanged(); }} />}
          {list.data && groups.length === 0 && <EmptyState title={empty} />}
          {groups.map((g) => <GroupCard key={`${g.kind}-${g.confidence}-${g.projectId}`} group={g} label={groupLabel(g, projects, t)} projects={projects} run={run} ids={ids} />)}
        </div>
      </Section>
      {children}
    </>
  );
}
