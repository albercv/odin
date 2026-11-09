import { useState } from 'react';
import { api, type S } from '../api/client';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';

function IssuedKey({ issued, onClose }: { issued: S['IssuedKeyView']; onClose: () => void }) {
  return (
    <div className="issued" role="status">
      <p><strong>No se volverá a mostrar.</strong> Guárdala ahora.</p>
      <code>{issued.key}</code>
      <button type="button" onClick={() => navigator.clipboard.writeText(issued.key ?? '')}>Copiar</button>
      <button type="button" onClick={onClose}>Entendido</button>
    </div>
  );
}

function KeyRow({ k, onRevoke }: { k: S['KeyView']; onRevoke: () => void }) {
  return (
    <li>
      {k.label} ({k.prefix}) {k.revokedAt ? '· revocada' : <button type="button" onClick={onRevoke}>Revocar</button>}
    </li>
  );
}

export function Keys({ userId }: { userId: string }) {
  const keys = useLoad(() => api.keys(userId), [userId]);
  const [label, setLabel] = useState('');
  const [issued, setIssued] = useState<S['IssuedKeyView']>();
  const { error, act } = useAction();
  const give = async (e: React.FormEvent) => {
    e.preventDefault();
    await act(async () => setIssued(await api.issueKey(userId, label)));
    setLabel('');
    keys.reload();
  };
  const revoke = async (id?: string) => {
    if (id && (await act(() => api.revokeKey(id)))) keys.reload();
  };
  return (
    <section aria-label="Claves">
      <Notice message={error ?? keys.error} />
      {issued && <IssuedKey issued={issued} onClose={() => setIssued(undefined)} />}
      <ul>{keys.data?.map((k) => <KeyRow key={k.id} k={k} onRevoke={() => revoke(k.id)} />)}</ul>
      <form onSubmit={give}>
        <label>Etiqueta de la clave <input required value={label} onChange={(e) => setLabel(e.target.value)} /></label>
        <button type="submit">Dar clave</button>
      </form>
    </section>
  );
}
