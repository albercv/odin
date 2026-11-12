import { api, type S } from '../api/client';
import { formatLongDay } from '../pages/dates';
import { Notice } from './Notice';
import { useLoad } from './useLoad';
import styles from './TaggingBacklog.module.css';

const DAY_MS = 24 * 3600_000;

function pendingText(status: S['TaggingStatusView']): string {
  const count = status.pendingDocuments;
  const subject = count === 1 ? '1 documento espera a ser etiquetado.' : `${count} documentos esperan a ser etiquetados.`;
  if (!status.oldestPendingSince) return subject;
  return `${subject} El más antiguo espera desde el ${formatLongDay(status.oldestPendingSince, status.timezone)}.`;
}

function windowReminder({ window, oldestPendingSince }: S['TaggingStatusView']): string | undefined {
  const late = oldestPendingSince && Date.now() - new Date(oldestPendingSince).getTime() > DAY_MS;
  if (!late || window.always) return undefined;
  return `El etiquetado se hace entre las ${window.from} y las ${window.to}: el equipo tiene que estar encendido a esas horas.`;
}

/** `version` changes when the topic list does, so the count is read again. */
export function TaggingBacklog({ companyId, version }: { companyId: string; version: number }) {
  const { data, error } = useLoad(() => api.taggingStatus(companyId), [companyId, version]);
  const reminder = data && windowReminder(data);
  return (
    <div className={styles.backlog}>
      <Notice message={error} />
      {data && data.pendingDocuments === 0 && <p>Todos los documentos están etiquetados.</p>}
      {data && data.pendingDocuments > 0 && <p>{pendingText(data)}{reminder && <> {reminder}</>}</p>}
    </div>
  );
}
