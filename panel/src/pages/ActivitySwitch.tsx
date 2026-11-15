import { useState } from 'react';
import { api, ApiError } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { Section } from '../ui/Section';

const WRONG_PASSWORD = 'La contraseña actual no es correcta.';
const MISSING_REFERENCE = 'Indica dónde está publicada la política de uso.';

function SwitchForm({ enable, onDone }: { enable: boolean; onDone: () => void }) {
  const [reference, setReference] = useState('');
  const [password, setPassword] = useState('');
  const { error, act } = useAction();
  const [shown, setShown] = useState<string>();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setShown(undefined);
    const body = enable ? { enabled: true, usagePolicyReference: reference.trim(), password } : { enabled: false, password };
    try { if (await act(() => api.updateAuditSettings(body).catch((x) => { throw explain(x); }))) onDone(); } catch { /* shown by act */ }
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error ?? shown} />
      {enable && <Field label="Referencia de la política de uso publicada"><input required value={reference} onChange={(e) => setReference(e.target.value)} /></Field>}
      <Field label="Contraseña actual"><input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
      <div><Button type="submit" variant={enable ? 'primary' : 'danger'}>{enable ? 'Encender' : 'Apagar'}</Button></div>
    </form>
  );
}

const explain = (e: unknown) => (e instanceof ApiError && e.status === 403 ? new Error(WRONG_PASSWORD)
  : e instanceof ApiError && e.status === 400 ? new Error(MISSING_REFERENCE) : e);

function ChatForm({ enable, onDone }: { enable: boolean; onDone: () => void }) {
  const [password, setPassword] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.updateAuditSettings({ chatEnabled: enable, password }).catch((x) => { throw explain(x); }))) onDone();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label="Contraseña actual"><input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
      <div><Button type="submit" variant={enable ? 'primary' : 'danger'}>{enable ? 'Activar' : 'Desactivar'}</Button></div>
    </form>
  );
}

const LEGAL_NOTE = 'Lo que se consulta por el chat llega a la IA del administrador, que es un tercero. Actívalo solo tras la validación legal.';

function ChatSwitch({ enabled, onDone }: { enabled: boolean; onDone: () => void }) {
  const [dialog, setDialog] = useState(false);
  const title = enabled ? 'Desactivar la consulta desde el chat' : 'Activar la consulta desde el chat';
  return (
    <Section title="Consulta desde el chat" description={LEGAL_NOTE}>
      <Button variant={enabled ? 'danger' : 'primary'} onClick={() => setDialog(true)}>{title}</Button>
      <FormDialog title={title} open={dialog} onClose={() => setDialog(false)}>
        <ChatForm enable={!enabled} onDone={() => { setDialog(false); onDone(); }} />
      </FormDialog>
    </Section>
  );
}

export function ActivitySwitch({ onChanged }: { onChanged: () => void }) {
  const settings = useLoad(api.auditSettings, []);
  const [dialog, setDialog] = useState(false);
  if (!settings.data) return <Notice message={settings.error} />;
  const enabled = settings.data.enabled;
  const changed = () => { settings.reload(); onChanged(); };
  return (
    <>
      <Section title="Interruptor del registro" description={enabled ? 'El registro está encendido.' : 'El registro está apagado.'}>
        <Button variant={enabled ? 'danger' : 'primary'} onClick={() => setDialog(true)}>{enabled ? 'Apagar el registro' : 'Encender el registro'}</Button>
        <FormDialog title={enabled ? 'Apagar el registro' : 'Encender el registro'} open={dialog} onClose={() => setDialog(false)}>
          <SwitchForm enable={!enabled} onDone={() => { setDialog(false); changed(); }} />
        </FormDialog>
      </Section>
      <ChatSwitch enabled={settings.data.chatEnabled} onDone={changed} />
    </>
  );
}
