import { Fragment, useRef, useState } from 'react';
import { api, type S } from '../api/client';
import { useConfirm } from '../ui/ConfirmDialog';
import { FormDialog } from '../ui/FormDialog';
import { Pagination } from '../ui/Pagination';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { SearchBox } from '../ui/SearchBox';
import { StatusPill } from '../ui/StatusPill';
import { Table, Td, Tr, tableStyles } from '../ui/Table';
import { useToast } from '../ui/Toast';
import { Keys } from './Keys';
import { MemberAccesses } from './MemberAccesses';
import { EditForm, ResetPasswordForm, RolesForm } from './MemberEditors';
import { MemberForm } from './MemberForm';
import { Notice } from './Notice';
import { confirmDeactivatePerson, confirmDeletePerson } from './personConfirmations';
import { useAction, useLoad } from './useLoad';
import { includesText, usePagedList } from './usePagedList';
import styles from './Members.module.css';

type Member = S['MemberView'];

function roleLabel(roles: Member['roles']) {
  const labels = [roles.includes('COMPANY_ADMIN') && 'Administra la empresa', roles.includes('WORKER') && 'Trabaja con su IA'];
  return labels.filter(Boolean).join(' · ');
}

/** Lets an address wrap only before «@» and before dots. */
function BreakableEmail({ value }: { value: string }) {
  return value.split(/(?=[@.])/).map((part, i) => <Fragment key={i}>{i > 0 && <wbr />}{part}</Fragment>);
}

type Panel = 'edit' | 'roles' | 'keys' | 'accesses' | 'password' | undefined;
type RowProps = {
  companyId: string; u: Member; isGroup: boolean; meId: string; onChanged: () => void; onRemoved: (announcement: string) => void; onProjectsChanged: () => void;
};

function MemberPanel({ companyId, u, isGroup, panel, onDone, onReset, onProjectsChanged }: RowProps & {
  panel: Panel; onDone: (saved: boolean) => void; onReset: () => void;
}) {
  if (panel === 'edit') return <EditForm user={u} onDone={onDone} />;
  if (panel === 'roles') return <RolesForm companyId={companyId} user={u} canGiveAdmin={isGroup} onDone={onDone} />;
  if (panel === 'keys') return <Keys companyId={companyId} userId={u.userId} memberName={u.displayName} />;
  if (panel === 'accesses') return <MemberAccesses companyId={companyId} member={u} onChanged={onProjectsChanged} />;
  return <ResetPasswordForm user={u} onDone={onReset} />;
}

function useRemovals(companyId: string, u: Member, onRemoved: RowProps['onRemoved'], act: ReturnType<typeof useAction>['act']) {
  const confirm = useConfirm();
  const remove = async () => {
    const message = `¿Quitar a ${u.displayName} de esta empresa? Sus claves y sus proyectos de esta empresa dejarán de valer.`;
    if (!(await confirm({ message, confirmLabel: 'Quitar', danger: true }))) return;
    if (await act(() => api.removeMember(companyId, u.userId))) onRemoved(`Se ha quitado a ${u.displayName} de esta empresa`);
  };
  const erase = async () => {
    const message = confirmDeletePerson(u.displayName);
    if (!(await confirm({ message, confirmLabel: 'Borrar', danger: true }))) return;
    if (await act(() => api.deleteUser(u.userId))) onRemoved(`Se ha borrado a ${u.displayName}`);
  };
  return { remove, erase };
}

/** Deactivating a person affects the whole group, so it asks first; reactivating does not. */
function useGroupToggle(u: Member, act: ReturnType<typeof useAction>['act'], onChanged: () => void) {
  const confirm = useConfirm();
  return async () => {
    if (u.personActive) {
      const message = confirmDeactivatePerson(u.displayName);
      if (!(await confirm({ message, confirmLabel: 'Desactivar', danger: true }))) return;
    }
    if (await act(() => api.updateUser(u.userId, { active: !u.personActive }))) onChanged();
  };
}

function MemberActions({ companyId, u, isGroup, meId, onChanged, onRemoved, panel, show }: RowProps & { panel: Panel; show: (p: Panel) => void }) {
  const { error, act } = useAction();
  const worker = u.roles.includes('WORKER');
  // The API only lets the group admin deactivate or remove a company admin, and nobody does it to themselves.
  const mayDisable = u.userId !== meId && (isGroup || !u.roles.includes('COMPANY_ADMIN'));
  const toggle = async () => (await act(() => api.setActive(companyId, u.userId, !u.active))) && onChanged();
  const toggleGroup = useGroupToggle(u, act, onChanged);
  const { remove, erase } = useRemovals(companyId, u, onRemoved, act);
  const open = (target: Panel, text: string, label?: string) => (
    <RowAction variant="ghost" text={text} label={label} target={u.displayName} aria-expanded={panel === target} onClick={() => show(target)} />
  );
  return (
    <>
      <RowActions label={`Más acciones de ${u.displayName}`}>
        {mayDisable && <RowAction variant="ghost" text={u.active ? 'Desactivar' : 'Activar'} target={u.displayName} onClick={toggle} />}
        {isGroup && u.userId !== meId && (
          <RowAction variant="ghost" text={u.personActive ? 'Desactivar en el grupo' : 'Activar en el grupo'} target={u.displayName} onClick={toggleGroup} />
        )}
        {open('edit', 'Editar')}
        {open('roles', 'Papeles', 'Papeles de')}
        {mayDisable && <RowAction variant="danger" text="Quitar" target={u.displayName} onClick={remove} />}
        {isGroup && <RowAction variant="danger" text="Borrar" target={u.displayName} onClick={erase} />}
        {worker && open('keys', 'Claves', 'Claves de')}
        {worker && open('accesses', 'Accesos', 'Accesos de')}
        {isGroup && u.roles.includes('COMPANY_ADMIN') && open('password', 'Restablecer contraseña', 'Restablecer contraseña de')}
      </RowActions>
      <Notice message={error} />
    </>
  );
}

function AccessState({ u }: { u: Member }) {
  return (
    <>
      <StatusPill tone={u.active ? 'ok' : 'off'}>{u.active ? 'Activo' : 'Desactivado'}</StatusPill>
      {!u.personActive && <span className={styles.flag}><StatusPill tone="off">Desactivada en el grupo</StatusPill></span>}
    </>
  );
}

function MemberRow(props: RowProps) {
  const { u, onChanged } = props;
  const [panel, setPanel] = useState<Panel>();
  const toast = useToast();
  const show = (p: Panel) => setPanel(panel === p ? undefined : p);
  const closed = (saved: boolean) => { setPanel(undefined); if (saved) onChanged(); };
  return (
    <>
      <Tr>
        <Td label="Código"><code className={styles.code}>{u.memberCode}</code></Td>
        <Td label="Persona" className={tableStyles.person}>
          <strong>{u.displayName}</strong><span>{u.email ? <BreakableEmail value={u.email} /> : 'sin correo'}</span>
          {!u.lastName && <span className={styles.flag}><StatusPill tone="pending">Faltan apellidos</StatusPill></span>}
        </Td>
        <Td label="Papel">{roleLabel(u.roles)}</Td>
        <Td label="Acceso"><AccessState u={u} /></Td>
        <Td className={tableStyles.actions}><MemberActions {...props} panel={panel} show={show} /></Td>
      </Tr>
      {panel && (
        <Tr className={tableStyles.panelRow}>
          <Td colSpan={5}>
            <MemberPanel {...props} panel={panel} onDone={closed} onReset={() => { setPanel(undefined); toast('Contraseña restablecida'); }} />
          </Td>
        </Tr>
      )}
    </>
  );
}

const NONE: Member[] = [];
const byNameOrEmail = (m: Member, q: string) => includesText(`${m.displayName} ${m.email ?? ''}`, q);

type Props = {
  companyId: string; me: S['AdminMe']; addOpen?: boolean; onAddClose?: () => void; onProjectsChanged?: () => void;
};

export function Members({ companyId, me, addOpen = false, onAddClose = () => undefined, onProjectsChanged = () => undefined }: Props) {
  const list = useLoad(() => api.members(companyId), [companyId]);
  const paged = usePagedList(list.data ?? NONE, byNameOrEmail, 10);
  const [region, toast, isGroup] = [useRef<HTMLElement>(null), useToast(), me.role === 'GROUP_ADMIN'];
  const removed = (announcement: string) => {
    list.reload();
    onProjectsChanged();
    toast(announcement);
    region.current?.focus();
  };
  return (
    <Section title="Personas" description="Cada persona usa Odín desde su IA con su clave personal.">
      <Notice message={list.error} />
      <SearchBox label="Buscar personas" onSearch={paged.setQuery} resultCount={paged.total} />
      <section ref={region} tabIndex={-1} aria-label="Lista de personas" className={styles.region}>
        <Table columns={['Código', 'Persona', 'Papel', 'Acceso']}>
          {paged.visible.map((u) => (
            <MemberRow key={u.userId} companyId={companyId} u={u} isGroup={isGroup} meId={me.userId} onChanged={list.reload}
              onRemoved={removed} onProjectsChanged={onProjectsChanged} />
          ))}
        </Table>
      </section>
      <Pagination label="Páginas de personas" page={paged.page} pageCount={paged.pageCount} onPage={paged.setPage} />
      <FormDialog title="Añadir persona" open={addOpen} onClose={onAddClose}>
        <MemberForm companyId={companyId} isGroupAdmin={isGroup} onDone={() => { onAddClose(); list.reload(); onProjectsChanged(); }} />
      </FormDialog>
    </Section>
  );
}
