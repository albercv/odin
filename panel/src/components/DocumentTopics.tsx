import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { useToast } from '../ui/Toast';
import { AiMark } from './AiMark';
import { Notice } from './Notice';
import { TaggingStatus } from './TaggingStatus';
import { useAction, useLoad } from './useLoad';
import styles from './DocumentTopics.module.css';

type Topic = S['DocumentTopicView'];
type Change = (fn: () => Promise<unknown>, message?: string) => void;

function ActiveTopics({ topics, change, documentId }: { topics: Topic[]; change: Change; documentId: string }) {
  if (topics.length === 0) return <p className={styles.none}>Este documento no tiene temas.</p>;
  return (
    <ul className={styles.list} aria-label="Temas del documento">
      {topics.map((t) => (
        <li key={t.topicId}>
          <span>{t.name}</span>{t.source === 'AI' && <AiMark />}
          <Button variant="ghost" aria-label={`Quitar ${t.name}`} onClick={() => change(() => api.removeDocumentTopic(documentId, t.topicId))}>Quitar</Button>
        </li>
      ))}
    </ul>
  );
}

function RemovedTopics({ topics, change, documentId }: { topics: Topic[]; change: Change; documentId: string }) {
  if (topics.length === 0) return null;
  return (
    <div className={styles.removed}>
      <h3>Quitados</h3>
      <ul className={styles.list}>
        {topics.map((t) => (
          <li key={t.topicId}>
            <span>{t.name}</span>
            <span className={styles.by}>{t.decidedByName ? `Quitado por ${t.decidedByName}` : 'Quitado'}</span>
            <Button variant="ghost" aria-label={`Recuperar ${t.name}`} onClick={() => change(() => api.addDocumentTopic(documentId, t.topicId))}>Recuperar</Button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function AddTopic({ options, onAdd }: { options: S['TopicView'][]; onAdd: (topicId: string) => void }) {
  const [choice, setChoice] = useState('');
  return (
    <div className={styles.add}>
      <Field label="Añadir tema">
        <select value={choice} onChange={(e) => setChoice(e.target.value)}>
          <option value="">Elige un tema</option>
          {options.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select>
      </Field>
      <Button disabled={!choice} onClick={() => { onAdd(choice); setChoice(''); }}>Añadir</Button>
    </div>
  );
}

export function DocumentTopics({ documentId, companyId }: { documentId: string; companyId: string }) {
  const own = useLoad(() => api.documentTopics(documentId), [documentId]);
  const company = useLoad(() => api.topics(companyId), [companyId]);
  const { error, act } = useAction();
  const toast = useToast();
  const change: Change = async (fn, message) => {
    if (!(await act(fn))) return;
    if (message) toast(message);
    own.reload();
  };
  const all = own.data?.topics ?? [];
  const active = all.filter((t) => t.state === 'ACTIVE');
  const removed = all.filter((t) => t.state === 'REMOVED');
  const options = (company.data ?? []).filter((t) => !all.some((o) => o.topicId === t.id));
  return (
    <section className={styles.topics} aria-label="Temas">
      <h3>Temas <TaggingStatus status={own.data?.tagging?.status} /></h3>
      <Notice message={error ?? own.error ?? company.error} />
      <ActiveTopics topics={active} change={change} documentId={documentId} />
      <AddTopic options={options} onAdd={(topicId) => change(() => api.addDocumentTopic(documentId, topicId))} />
      <RemovedTopics topics={removed} change={change} documentId={documentId} />
      <div><Button onClick={() => change(() => api.retag(documentId), 'El documento se volverá a etiquetar')}>Volver a etiquetar</Button></div>
    </section>
  );
}
