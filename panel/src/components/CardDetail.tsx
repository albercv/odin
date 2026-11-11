import { useEffect, useRef, useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { Icon } from '../ui/Icon';
import { StatusPill } from '../ui/StatusPill';
import { useToast } from '../ui/Toast';
import { formatDate } from '../pages/reviewNames';
import { Notice } from './Notice';
import { useAction } from './useLoad';
import styles from './CardDetail.module.css';

type Props = { card: S['CardView']; author: string; project: string; locked?: boolean; focusTitle?: boolean; onResolved: () => void };

function Facts({ card, author, project }: Omit<Props, 'onResolved' | 'locked' | 'focusTitle'>) {
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

function RejectPanel({ busy, onReject }: { busy: boolean; onReject: (note: string) => void }) {
  const [note, setNote] = useState('');
  return (
    <div className={styles.reject}>
      <Field label="Motivo del rechazo"><input autoFocus placeholder="Lo verá quien guardó la ficha" value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      <div><Button variant="danger" disabled={!note.trim() || busy} onClick={() => onReject(note)}>Rechazar la ficha</Button></div>
    </div>
  );
}

type Send = (review: S['Review'], message: string) => void;

function Actions({ body, changed, busy, send }: { body: string; changed: boolean; busy: boolean; send: Send }) {
  const [rejecting, setRejecting] = useState(false);
  return (
    <>
      <div className={styles.actions}>
        <Button variant="primary" disabled={busy} onClick={() => send({ decision: 'APPROVE' }, 'Ficha aprobada')}>Aprobar</Button>
        <Button disabled={!changed || busy} onClick={() => send({ decision: 'CORRECT', correctedBody: body }, 'Ficha corregida y aprobada')}>Corregir y aprobar</Button>
        <Button variant="danger" disabled={busy} aria-expanded={rejecting} onClick={() => setRejecting(true)}>Rechazar</Button>
      </div>
      {rejecting && <RejectPanel busy={busy} onReject={(note) => send({ decision: 'REJECT', note }, 'Ficha rechazada')} />}
    </>
  );
}

function useSend(cardId: string, locked: boolean, onResolved: () => void) {
  const [sending, setSending] = useState(false);
  const inFlight = useRef(false);
  const { error, act } = useAction();
  const toast = useToast();
  const send: Send = async (review, message) => {
    if (inFlight.current || locked) return;
    inFlight.current = true;
    setSending(true);
    if (await act(() => api.review(cardId, review))) {
      toast(message);
      onResolved();
      return;
    }
    inFlight.current = false;
    setSending(false);
  };
  return { send, sending, error };
}

export function CardDetail({ card, author, project, locked = false, focusTitle = false, onResolved }: Props) {
  const title = useRef<HTMLHeadingElement>(null);
  useEffect(() => { if (focusTitle) title.current?.focus(); }, []);
  const [body, setBody] = useState(card.body);
  const { send, sending, error } = useSend(card.id, locked, onResolved);
  const changed = body.trim() !== card.body.trim();
  return (
    <article className={styles.detail}>
      <div className={styles.top}>
        <StatusPill tone="pending">Pendiente de revisión</StatusPill>
        <span className={styles.origin}><Icon name="spark" />Ficha redactada por la IA de {author}</span>
      </div>
      <h2 ref={title} tabIndex={-1}>{card.title}</h2>
      <Facts card={card} author={author} project={project} />
      <Notice message={error} />
      <Field label="Texto de la ficha"><textarea value={body} onChange={(e) => setBody(e.target.value)} /></Field>
      {changed && <Original text={card.body} />}
      <Actions body={body} changed={changed} busy={sending || locked} send={send} />
    </article>
  );
}
