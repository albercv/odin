import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { Icon } from '../ui/Icon';
import { StatusPill } from '../ui/StatusPill';
import { useToast } from '../ui/Toast';
import { Notice } from './Notice';
import { useAction } from './useLoad';
import styles from './CardDetail.module.css';

type Props = { card: S['CardView']; author: string; project: string; onResolved: () => void };

const formatDate = (iso: string) => new Date(iso).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });

function Facts({ card, author, project }: Omit<Props, 'onResolved'>) {
  return (
    <dl className={styles.facts}>
      <div><dt>Proyecto</dt><dd>{project}</dd></div>
      <div><dt>Guardada por</dt><dd>{author}</dd></div>
      <div><dt>Fecha</dt><dd>{formatDate(card.createdAt)}</dd></div>
    </dl>
  );
}

function Original({ text }: { text: string }) {
  const [shown, setShown] = useState(false);
  return (
    <>
      <div className={styles.edited}>
        <span>Has cambiado el texto.</span>
        <button type="button" className={styles.link} aria-expanded={shown} onClick={() => setShown(!shown)}>Ver el original</button>
      </div>
      {shown && <div role="note" aria-label="Texto original" className={styles.original}>{text}</div>}
    </>
  );
}

function RejectPanel({ onReject }: { onReject: (note: string) => void }) {
  const [note, setNote] = useState('');
  return (
    <div className={styles.reject}>
      <Field label="Motivo del rechazo"><input autoFocus placeholder="Lo verá quien guardó la ficha" value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      <div><Button variant="danger" disabled={!note.trim()} onClick={() => onReject(note)}>Rechazar la ficha</Button></div>
    </div>
  );
}

type Send = (review: S['Review'], message: string) => void;

function Actions({ body, changed, send }: { body: string; changed: boolean; send: Send }) {
  const [rejecting, setRejecting] = useState(false);
  return (
    <>
      <div className={styles.actions}>
        <Button variant="primary" onClick={() => send({ decision: 'APPROVE' }, 'Ficha aprobada')}>Aprobar</Button>
        <Button disabled={!changed} onClick={() => send({ decision: 'CORRECT', correctedBody: body }, 'Ficha corregida y aprobada')}>Corregir y aprobar</Button>
        <Button variant="danger" aria-expanded={rejecting} onClick={() => setRejecting(true)}>Rechazar</Button>
      </div>
      {rejecting && <RejectPanel onReject={(note) => send({ decision: 'REJECT', note }, 'Ficha rechazada')} />}
    </>
  );
}

export function CardDetail({ card, author, project, onResolved }: Props) {
  const [body, setBody] = useState(card.body);
  const { error, act } = useAction();
  const toast = useToast();
  const changed = body.trim() !== card.body.trim();
  const send: Send = async (review, message) => {
    if (!(await act(() => api.review(card.id, review)))) return;
    toast(message);
    onResolved();
  };
  return (
    <article className={styles.detail}>
      <div className={styles.top}>
        <StatusPill tone="pending">Pendiente de revisión</StatusPill>
        <span className={styles.origin}><Icon name="spark" />Ficha redactada por la IA de {author}</span>
      </div>
      <h2>{card.title}</h2>
      <Facts card={card} author={author} project={project} />
      <Notice message={error} />
      <Field label="Texto de la ficha"><textarea value={body} onChange={(e) => setBody(e.target.value)} /></Field>
      {changed && <Original text={card.body} />}
      <Actions body={body} changed={changed} send={send} />
    </article>
  );
}
