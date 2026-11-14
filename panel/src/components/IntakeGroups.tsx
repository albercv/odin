import { useState, type ReactNode } from 'react';
import { intake, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { EmptyState } from '../ui/EmptyState';
import { Section } from '../ui/Section';
import type { Step } from './IntakeCandidates';
import { IntakeProposalRow, type Decision } from './IntakeProposalRow';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './Intake.module.css';

type Group = S['IntakeProposalGroup'];
type Run = (fn: () => Promise<unknown>) => void;

const KIND: Record<string, string> = {
  COMPANY_WIDE: 'De toda la empresa', SAME_AS_SCAN: 'Escaneos y su copia', SUPERSEDES: 'Versiones',
  CASE_MARK: 'Documentos de caso', CASE_LINK: 'Enlaces a fichas de caso',
};
const noun = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
const EFFECT: Record<string, (n: number, project: string) => string> = {
  PROJECT: (n, project) => `¿Confirmar ${noun(n, 'documento', 'documentos')} en ${project}? Desde ese momento los verán quienes ven el proyecto.`,
  COMPANY_WIDE: (n) => `¿Confirmar ${noun(n, 'documento', 'documentos')} como de toda la empresa? Desde ese momento los verá toda la empresa.`,
  SAME_AS_SCAN: (n) => `¿Confirmar ${noun(n, 'pareja', 'parejas')} de escaneo y copia? Cada escaneo quedará enlazado con su copia.`,
  SUPERSEDES: (n) => `¿Confirmar ${noun(n, 'versión', 'versiones')}? Cada una sustituirá a la anterior.`,
  CASE_MARK: (n) => `¿Marcar ${noun(n, 'documento', 'documentos')} como documentos de caso?`,
  CASE_LINK: (n) => `¿Confirmar ${noun(n, 'documento', 'documentos')} en sus fichas de caso? Se añadirán a la ficha con el número que citan.`,
};

const groupLabel = (g: Group, projects: S['ProjectView'][]) => {
  const project = projects.find((p) => p.id === g.projectId)?.name;
  if (g.kind === 'PROJECT') return project ?? 'Proyecto sin nombre';
  return project ? `${KIND[g.kind] ?? g.kind} (${project})` : KIND[g.kind] ?? g.kind;
};

/** The API takes at most 500 proposals per request (each one all or nothing): bigger groups go in parts. */
const PART = 500;
const PARTS_NOTE = ` Va en partes de hasta ${PART} propuestas y cada parte es todo o nada.`;

async function confirmInParts(ids: { company: string; batch: string }, proposalIds: string[]) {
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

type GroupProps = { group: Group; label: string; projects: S['ProjectView'][]; run: Run; ids: { company: string; batch: string } };

function GroupCard({ group, label, projects, run, ids }: GroupProps) {
  const [open, setOpen] = useState(false);
  const confirm = useConfirm();
  const count = group.proposals.length;
  // never ask to confirm documents in a project whose name is not known (D2: informed consent)
  const unknownProject = group.kind === 'PROJECT' && !projects.some((p) => p.id === group.projectId);
  const confirmGroup = async () => {
    const message = (EFFECT[group.kind]?.(count, label) ?? `¿Confirmar ${noun(count, 'documento', 'documentos')}?`) + (count > PART ? PARTS_NOTE : '');
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
          {group.proposals.map((p) => <IntakeProposalRow key={p.id} kind={group.kind} item={p} projectKind={group.kind === 'PROJECT' || group.kind === 'COMPANY_WIDE'} projects={projects} decide={decide} />)}
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
  // refresh also after a failure: part of a group may have been confirmed
  const run: Run = async (fn) => { await act(fn); list.reload(); onChanged(); };
  const groups = (list.data?.groups ?? []).filter((g) => kinds.includes(g.kind));
  return (
    <>
      <Section title={title} description={description}>
        <div className={styles.body}>
          <Notice message={list.error ?? error} />
          {list.data && groups.length === 0 && <EmptyState title={empty} />}
          {groups.map((g) => <GroupCard key={`${g.kind}-${g.confidence}-${g.projectId}`} group={g} label={groupLabel(g, projects)} projects={projects} run={run} ids={{ company: companyId, batch: batchId }} />)}
        </div>
      </Section>
      {children}
    </>
  );
}
