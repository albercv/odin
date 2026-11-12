import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { RowAction } from '../ui/RowActions';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './Keys.module.css';

function IssuedKeyDialog({ issued, onClose }: { issued: S['IssuedKeyView']; onClose: () => void }) {
  return (
    <FormDialog title="Clave nueva" open dismissible={false} onClose={onClose}>
      <p><strong>No se volverá a mostrar.</strong> Guárdala ahora.</p>
      <code className={styles.key}>{issued.key}</code>
      <div className={form.buttons}>
        <Button onClick={() => navigator.clipboard.writeText(issued.key)}>Copiar</Button>
        <Button variant="primary" onClick={onClose}>Entendido</Button>
      </div>
    </FormDialog>
  );
}

function KeyRow({ k, onRevoke }: { k: S['KeyView']; onRevoke: () => void }) {
  return (
    <li className={styles.item}>
      <span>{k.label} ({k.prefix})</span>
      {k.revokedAt
        ? <span className={styles.revoked}>Revocada</span>
        : <RowAction variant="danger" text="Revocar clave" target={k.label} onClick={onRevoke} />}
    </li>
  );
}

export function Keys({ companyId, userId, memberName }: { companyId: string; userId: string; memberName: string }) {
  const keys = useLoad(() => api.keys(companyId, userId), [companyId, userId]);
  const [label, setLabel] = useState('');
  const [issued, setIssued] = useState<S['IssuedKeyView']>();
  const { error, act } = useAction();
  const give = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(async () => setIssued(await api.issueKey(companyId, userId, label))))) return;
    setLabel('');
    keys.reload();
  };
  const revoke = async (id: string) => {
    if (await act(() => api.revokeKey(id))) keys.reload();
  };
  return (
    <section aria-label="Claves" className={form.form}>
      <Notice message={error ?? keys.error} />
      {issued && <IssuedKeyDialog key={issued.id} issued={issued} onClose={() => setIssued(undefined)} />}
      <p className={styles.explanation}>Cada clave da acceso a lo mismo que ve {memberName} en esta empresa.</p>
      <ul className={styles.list}>{keys.data?.map((k) => <KeyRow key={k.id} k={k} onRevoke={() => revoke(k.id)} />)}</ul>
      <form className={form.form} onSubmit={give}>
        <Field label="Etiqueta de la clave"><input required value={label} onChange={(e) => setLabel(e.target.value)} /></Field>
        <div><Button type="submit" variant="primary">Dar clave</Button></div>
      </form>
    </section>
  );
}
