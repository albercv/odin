import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';
import styles from './DocumentDetail.module.css';

type Link = S['DocumentLinkView'];
type Props = { documentId: string; onChanged: () => void };

const RELATION: Record<string, [string, string]> = {
  RECTIFIES: ['Rectifica a', 'Rectificado por'],
  REPLACES: ['Sustituye a', 'Sustituido por'],
  MODIFIES: ['Modifica a', 'Modificado por'],
};
const STATUS: Record<string, string> = { DETECTED: 'Detectado', CONFIRMED: 'Confirmado', DISMISSED: 'Descartado' };

const relationLabel = (l: Link) => RELATION[l.relation]?.[l.direction === 'corrects' ? 0 : 1] ?? l.relation;

function Versions({ versions }: { versions: S['VersionView'][] }) {
  if (versions.length < 2) return null;
  return (
    <section>
      <h3>Versiones</h3>
      <ol>
        {versions.map((v) => (
          <li key={v.documentId}>{v.title}{v.current && <> · <strong>Vigente</strong></>}</li>
        ))}
      </ol>
    </section>
  );
}

function LinkItem({ link, onConfirm, onDismiss }: { link: Link; onConfirm: () => void; onDismiss: () => void }) {
  const supersedes = link.relation !== 'MODIFIES';
  return (
    <li>
      <strong>{relationLabel(link)}</strong>{' '}
      {link.title ?? `un documento que aún no está en Odín (${link.citedIdentifier})`}
      {' · '}<span>{STATUS[link.status] ?? link.status}</span>
      <blockquote>{link.evidenceQuote} <span>{`pág. ${link.evidencePage}`}</span></blockquote>
      {link.status === 'DETECTED' && (
        <div>
          {link.documentId && <Button onClick={onConfirm}>{supersedes ? 'Confirmar como sustitución' : 'Confirmar'}</Button>}{' '}
          <Button onClick={onDismiss}>Descartar</Button>
        </div>
      )}
    </li>
  );
}

const CONFIRM = 'Odín dará el documento anterior como sustituido y lo pondrá detrás en las búsquedas. Lo decides tú, no la detección.';
const CONFIRM_RELATION = 'Odín dejará este vínculo como confirmado. No marca ninguna sustitución.';
const DISMISS = 'El vínculo dejará de salir en las respuestas. El documento no cambia.';

export function DocumentLinks({ documentId, onChanged }: Props) {
  const confirm = useConfirm();
  const { error, act } = useAction();
  const { data, error: loadError, reload } = useLoad(() => api.documentContext(documentId), [documentId]);
  const decide = async (ok: boolean, run: () => Promise<unknown>, message: string, label: string) => {
    if (await confirm({ message, confirmLabel: label, danger: !ok }) && await act(run)) { reload(); onChanged(); }
  };
  if (loadError) return <Notice message={loadError} />;
  if (!data) return null;
  return (
    <>
      <Notice message={error} />
      <Versions versions={data.versions} />
      <section className={styles.links}>
        <h3>Vínculos detectados</h3>
        {data.links.length === 0 && <p>Este documento no tiene vínculos con otros.</p>}
        <ul>
          {data.links.map((l) => (
            <LinkItem key={l.id} link={l}
              onConfirm={() => decide(true, () => api.confirmLink(l.id), l.relation === 'MODIFIES' ? CONFIRM_RELATION : CONFIRM, 'Sí, confirmar')}
              onDismiss={() => decide(false, () => api.dismissLink(l.id), DISMISS, 'Sí, descartar')} />
          ))}
        </ul>
      </section>
    </>
  );
}
