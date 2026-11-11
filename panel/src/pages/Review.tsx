import { useEffect, useState } from 'react';
import { api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';

function CardReview({ card, onDone }: { card: S['CardView']; onDone: () => void }) {
  const [body, setBody] = useState(card.body);
  const [note, setNote] = useState('');
  const { error, act } = useAction();
  const send = async (r: S['Review']) => (await act(() => api.review(card.id, r))) && onDone();
  return (
    <article className="card">
      <h2>{card.title}</h2>
      <Notice message={error} />
      <label>Texto corregido <textarea value={body} onChange={(e) => setBody(e.target.value)} /></label>
      <label>Motivo del rechazo <input value={note} onChange={(e) => setNote(e.target.value)} /></label>
      <button type="button" onClick={() => send({ decision: 'APPROVE' })}>Aprobar</button>
      <button type="button" onClick={() => send({ decision: 'CORRECT', correctedBody: body })}>Corregir y aprobar</button>
      <button type="button" disabled={!note.trim()} onClick={() => send({ decision: 'REJECT', note })}>Rechazar</button>
    </article>
  );
}

export function Review({ companyId, onPendingChanged }: { companyId: string; onPendingChanged?: (count: number) => void }) {
  const list = useLoad(() => api.pendingCards(companyId), [companyId]);
  useEffect(() => { if (list.data) onPendingChanged?.(list.data.length); }, [list.data]);
  return (
    <section>
      <h1>Revisión de fichas</h1>
      <Notice message={list.error} />
      {list.data?.length === 0 && <p>No hay fichas pendientes.</p>}
      {list.data?.map((c) => <CardReview key={c.id} card={c} onDone={list.reload} />)}
    </section>
  );
}
