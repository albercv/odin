import { useState } from 'react';
import { api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction } from '../components/useLoad';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { Section } from '../ui/Section';
import styles from './Activity.module.css';

function SealCheck() {
  const [id, setId] = useState('');
  const [hash, setHash] = useState('');
  const [matches, setMatches] = useState<boolean>();
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMatches(undefined);
    await act(async () => { setMatches((await api.checkSeal({ id: Number(id), hash: hash.trim() })).matches); });
  };
  return (
    <form className={styles.inline} onSubmit={submit}>
      <Field label="Id del sello"><input required inputMode="numeric" pattern="[0-9]+" value={id} onChange={(e) => setId(e.target.value)} /></Field>
      <Field label="Huella del sello"><input required value={hash} onChange={(e) => setHash(e.target.value)} /></Field>
      <Button type="submit">Comprobar</Button>
      <Notice message={error} />
      {matches === true && <p>El sello coincide: la entrada sigue igual y la cadena hasta ella es íntegra.</p>}
      {matches === false && <p role="alert">El sello no coincide: la entrada ha cambiado o la cadena está rota.</p>}
    </form>
  );
}

export function ActivityFooter({ integrity: i }: { integrity?: S['ActivityIntegrityView'] }) {
  const copy = () => navigator.clipboard.writeText(`${i?.headId} ${i?.headHash} ${i?.headAt}`);
  return (
    <Section title="Estado del registro">
      <div className={styles.foot}>
        {i && <p>{i.intact ? 'Registro íntegro' : `Cadena rota en la entrada ${i.firstBrokenId}`}</p>}
        {i?.headHash && (
          <p className={styles.hash}>Sello de cabeza: entrada {i.headId}, huella <code>{i.headHash}</code>, a {i.headAt && new Date(i.headAt).toLocaleString('es-ES')}{' '}
            <Button onClick={copy}>Copiar sello</Button></p>
        )}
        <h3>Comprobar un sello</h3>
        <SealCheck />
      </div>
    </Section>
  );
}
