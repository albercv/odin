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

export function ActivitySwitch({ onChanged }: { onChanged: () => void }) {
  const settings = useLoad(api.auditSettings, []);
  const [dialog, setDialog] = useState(false);
  if (!settings.data) return <Notice message={settings.error} />;
  const enabled = settings.data.enabled;
  return (
    <Section title="Interruptor del registro" description={enabled ? 'El registro está encendido.' : 'El registro está apagado.'}>
      <Button variant={enabled ? 'danger' : 'primary'} onClick={() => setDialog(true)}>{enabled ? 'Apagar el registro' : 'Encender el registro'}</Button>
      <FormDialog title={enabled ? 'Apagar el registro' : 'Encender el registro'} open={dialog} onClose={() => setDialog(false)}>
        <SwitchForm enable={!enabled} onDone={() => { setDialog(false); settings.reload(); onChanged(); }} />
      </FormDialog>
    </Section>
  );
}
