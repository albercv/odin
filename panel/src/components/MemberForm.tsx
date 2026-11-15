import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { useToast } from '../ui/Toast';
import { Notice } from './Notice';
import { RolesFields, rolesOf, type RoleChoice } from './RolesFields';
import { useAction, useLoad } from './useLoad';
import { useProjectTerms } from './projectTerms';
import { widelyVisible } from './widelyVisible';

const NO_ROLE = 'Elige al menos un papel';
const ALREADY_EXISTED = 'Esta persona ya existía en el grupo: se ha añadido a esta empresa';
const KEPT_PASSWORD = ' Conserva su contraseña.';

type Draft = { firstName: string; lastName: string; email: string; password: string; projectIds: string[] };
const EMPTY: Draft = { firstName: '', lastName: '', email: '', password: '', projectIds: [] };

function bodyOf(draft: Draft, choice: RoleChoice): S['AddMember'] {
  const body: S['AddMember'] = { firstName: draft.firstName, lastName: draft.lastName, roles: rolesOf(choice) };
  if (draft.email) body.email = draft.email;
  if (choice.admin) body.password = draft.password;
  if (choice.worker && draft.projectIds.length > 0) body.projectIds = draft.projectIds;
  return body;
}

/** Only closed projects need choosing; the company-wide ones are seen anyway. */
function ProjectChoices({ companyId, chosen, onChange }: { companyId: string; chosen: string[]; onChange: (ids: string[]) => void }) {
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const t = useProjectTerms();
  const closed = projects.data?.filter((p) => p.restricted) ?? [];
  const wide = (projects.data?.length ?? 0) - closed.length;
  if (closed.length === 0 && wide === 0) return null;
  const toggle = (id: string) => onChange(chosen.includes(id) ? chosen.filter((x) => x !== id) : [...chosen, id]);
  return (
    <fieldset className={form.group}>
      <legend>{t.Many} a {t.agree('los', 'las')} que accede</legend>
      {closed.map((p) => (
        <label key={p.id} className={form.check}>
          <input type="checkbox" checked={chosen.includes(p.id)} onChange={() => toggle(p.id)} />{p.name}
        </label>
      ))}
      {wide > 0 && <p className={form.hint}>{widelyVisible(wide, 'verá', t)}</p>}
    </fieldset>
  );
}

function PersonFields({ draft, set, emailRequired }: { draft: Draft; set: (changes: Partial<Draft>) => void; emailRequired: boolean }) {
  return (
    <>
      <Field label="Nombre"><input required value={draft.firstName} onChange={(e) => set({ firstName: e.target.value })} /></Field>
      <Field label="Apellidos"><input required value={draft.lastName} onChange={(e) => set({ lastName: e.target.value })} /></Field>
      <Field label="Correo"><input type="email" required={emailRequired} value={draft.email} onChange={(e) => set({ email: e.target.value })} /></Field>
    </>
  );
}

type Props = { companyId: string; isGroupAdmin: boolean; onDone: () => void };

export function MemberForm({ companyId, isGroupAdmin, onDone }: Props) {
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [choice, setChoice] = useState<RoleChoice>({ admin: false, worker: true });
  const [roleError, setRoleError] = useState<string>();
  const { error, act } = useAction();
  const toast = useToast();
  const set = (changes: Partial<Draft>) => setDraft({ ...draft, ...changes });
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!choice.admin && !choice.worker) return setRoleError(NO_ROLE);
    setRoleError(undefined);
    let added: S['MemberAdded'] | undefined;
    if (!(await act(async () => { added = await api.addMember(companyId, bodyOf(draft, choice)); }))) return;
    setDraft(EMPTY);
    if (added?.alreadyExisted) toast(ALREADY_EXISTED + (added.keptPassword ? `.${KEPT_PASSWORD}` : ''));
    onDone();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <PersonFields draft={draft} set={set} emailRequired={choice.admin} />
      <RolesFields choice={choice} onChange={setChoice} canGiveAdmin={isGroupAdmin} error={roleError} />
      {choice.admin && (
        <Field label="Contraseña"><input type="password" required minLength={12} value={draft.password} onChange={(e) => set({ password: e.target.value })} /></Field>
      )}
      {choice.worker && <ProjectChoices companyId={companyId} chosen={draft.projectIds} onChange={(projectIds) => set({ projectIds })} />}
      <div><Button type="submit" variant="primary">Añadir persona</Button></div>
    </form>
  );
}
