import { useState } from 'react';
import { api, ApiError, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { Section } from '../ui/Section';

const day = (iso: string) => new Date(iso).toLocaleDateString('es-ES');

const explain = (e: unknown) => (e instanceof ApiError && e.status === 403 ? new Error('La contraseña actual no es correcta.')
  : e instanceof ApiError && e.status === 409 ? new Error('La consulta desde el chat no está activada.')
    : e instanceof ApiError && e.status === 400 ? new Error('Elige la empresa de la clave.') : e);

function Issued({ token }: { token: string }) {
  return (
    <div className={form.form}>
      <code>{token}</code>
      <p>Guárdala ahora: no se volverá a mostrar. Caduca en 30 días.</p>
      <div><Button onClick={() => navigator.clipboard.writeText(token)}>Copiar</Button></div>
    </div>
  );
}

function IssueForm({ companies, onIssued }: { companies: S['CompanyRef'][]; onIssued: (token: string) => void }) {
  const [password, setPassword] = useState('');
  const [companyId, setCompanyId] = useState('');
  const { error, act } = useAction();
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    act(async () => onIssued((await api.issueActivityKey({ password, ...(companyId && { companyId }) }).catch((x) => { throw explain(x); })).token));
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      {companies.length > 1 && (
        <Field label="Empresa de la clave">
          <select required value={companyId} onChange={(e) => setCompanyId(e.target.value)}>
            <option value="">Elige una empresa</option>
            {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
      )}
      <Field label="Contraseña actual"><input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
      <div><Button type="submit" variant="primary">Emitir</Button></div>
    </form>
  );
}

/** `companies` is only passed for a company admin; the group admin's key has group scope. */
export function ActivityChat({ companies, version }: { companies: S['CompanyRef'][]; version: number }) {
  const keys = useLoad(api.activityKeys, [version]);
  const [dialog, setDialog] = useState(false);
  const [token, setToken] = useState<string>();
  const confirm = useConfirm();
  const { error, act } = useAction();
  if (!keys.data) return <Notice message={keys.error} />;
  const key = keys.data.key;
  const close = () => { setDialog(false); setToken(undefined); keys.reload(); };
  const revoke = async () => {
    if (key && await confirm({ message: `¿Revocar la clave ${key.prefix}? Dejará de funcionar en el chat.`, confirmLabel: 'Revocar la clave', danger: true })) {
      if (await act(() => api.revokeActivityKey(key.id))) keys.reload();
    }
  };
  if (!keys.data.chatAvailable) {
    return <Section title="Consulta desde el chat" description="La consulta desde el chat está desactivada. El administrador del grupo puede activarla en el interruptor." />;
  }
  return (
    <Section title="Consulta desde el chat"
      description={key ? `Clave vigente ${key.prefix}, creada el ${day(key.createdAt)}, caduca el ${day(key.expiresAt)}.` : 'No tienes ninguna clave vigente.'}>
      <Notice message={error} />
      <Button variant="primary" onClick={() => setDialog(true)}>Emitir clave</Button>
      {key && <Button variant="danger" onClick={revoke}>Revocar</Button>}
      <FormDialog title="Emitir clave" open={dialog} onClose={close}>
        {token ? <Issued token={token} /> : <IssueForm companies={companies} onIssued={setToken} />}
      </FormDialog>
    </Section>
  );
}
