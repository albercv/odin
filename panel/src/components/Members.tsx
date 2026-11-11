import { useState } from 'react';
import { api, type S } from '../api/client';
import { useConfirm } from '../ui/ConfirmDialog';
import { FormDialog } from '../ui/FormDialog';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, tableStyles } from '../ui/Table';
import { useToast } from '../ui/Toast';
import { Keys } from './Keys';
import { MemberAccesses } from './MemberAccesses';
import { EditForm, ResetPasswordForm, RolesForm } from './MemberEditors';
import { MemberForm } from './MemberForm';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './Members.module.css';

type Member = S['MemberView'];

function roleLabel(roles: Member['roles']) {
  const labels = [roles.includes('COMPANY_ADMIN') && 'Administra la empresa', roles.includes('WORKER') && 'Trabaja con su IA'];
  return labels.filter(Boolean).join(' · ');
}

type Panel = 'edit' | 'roles' | 'keys' | 'accesses' | 'password' | undefined;
type RowProps = { companyId: string; u: Member; isGroup: boolean; onChanged: () => void };

function MemberPanel({ companyId, u, isGroup, panel, onDone, onReset }: RowProps & {
  panel: Panel; onDone: (saved: boolean) => void; onReset: () => void;
}) {
  if (panel === 'edit') return <EditForm user={u} onDone={onDone} />;
  if (panel === 'roles') return <RolesForm companyId={companyId} user={u} canGiveAdmin={isGroup} onDone={onDone} />;
  if (panel === 'keys') return <Keys companyId={companyId} userId={u.userId} memberName={u.displayName} />;
  if (panel === 'accesses') return <MemberAccesses companyId={companyId} member={u} />;
  return <ResetPasswordForm user={u} onDone={onReset} />;
}

function MemberActions({ companyId, u, isGroup, onChanged, panel, show }: RowProps & { panel: Panel; show: (p: Panel) => void }) {
  const { error, act } = useAction();
  const confirm = useConfirm();
  const worker = u.roles.includes('WORKER');
  const toggle = async () => (await act(() => api.setActive(companyId, u.userId, !u.active))) && onChanged();
  const remove = async () => {
    const message = `¿Quitar a ${u.displayName} de esta empresa? Sus claves y sus proyectos de esta empresa dejarán de valer.`;
    if (!(await confirm({ message, confirmLabel: 'Quitar', danger: true }))) return;
    if (await act(() => api.removeMember(companyId, u.userId))) onChanged();
  };
  const open = (target: Panel, text: string, label?: string) => (
    <RowAction variant="ghost" text={text} label={label} target={u.displayName} aria-expanded={panel === target} onClick={() => show(target)} />
  );
  return (
    <>
      <RowActions label={`Más acciones de ${u.displayName}`}>
        <RowAction variant="ghost" text={u.active ? 'Desactivar' : 'Activar'} target={u.displayName} onClick={toggle} />
        {open('edit', 'Editar')}
        {open('roles', 'Papeles', 'Papeles de')}
        <RowAction variant="danger" text="Quitar" target={u.displayName} onClick={remove} />
        {worker && open('keys', 'Claves', 'Claves de')}
        {worker && open('accesses', 'Accesos', 'Accesos de')}
        {isGroup && u.roles.includes('COMPANY_ADMIN') && open('password', 'Restablecer contraseña', 'Restablecer contraseña de')}
      </RowActions>
      <Notice message={error} />
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
      <tr>
        <td data-label="Código"><code className={styles.code}>{u.memberCode}</code></td>
        <td className={tableStyles.person} data-label="Persona">
          <strong>{u.displayName}</strong><span>{u.email ?? 'sin correo'}</span>
          {!u.lastName && <span className={styles.flag}><StatusPill tone="pending">Faltan apellidos</StatusPill></span>}
        </td>
        <td data-label="Papel">{roleLabel(u.roles)}</td>
        <td data-label="Acceso"><StatusPill tone={u.active ? 'ok' : 'off'}>{u.active ? 'Activo' : 'Desactivado'}</StatusPill></td>
        <td className={tableStyles.actions}><MemberActions {...props} panel={panel} show={show} /></td>
      </tr>
      {panel && (
        <tr className={tableStyles.panelRow}>
          <td colSpan={5}>
            <MemberPanel {...props} panel={panel} onDone={closed} onReset={() => { setPanel(undefined); toast('Contraseña restablecida'); }} />
          </td>
        </tr>
      )}
    </>
  );
}

type Props = { companyId: string; me: S['AdminMe']; addOpen?: boolean; onAddClose?: () => void };

export function Members({ companyId, me, addOpen = false, onAddClose = () => undefined }: Props) {
  const list = useLoad(() => api.members(companyId), [companyId]);
  const isGroup = me.role === 'GROUP_ADMIN';
  return (
    <Section title="Personas" description="Cada persona usa Odín desde su IA con su clave personal.">
      <Notice message={list.error} />
      <Table columns={['Código', 'Persona', 'Papel', 'Acceso']}>
        {list.data?.map((u) => <MemberRow key={u.userId} companyId={companyId} u={u} isGroup={isGroup} onChanged={list.reload} />)}
      </Table>
      <FormDialog title="Añadir persona" open={addOpen} onClose={onAddClose}>
        <MemberForm companyId={companyId} isGroupAdmin={isGroup} onDone={() => { onAddClose(); list.reload(); }} />
      </FormDialog>
    </Section>
  );
}
