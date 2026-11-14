import { useState } from 'react';
import { ApiError, uploadLinks, type S } from '../api/client';
import { Field } from '../ui/Field';
import { Notice } from '../components/Notice';
import { useLoad } from '../components/useLoad';
import { formatDate } from './reviewNames';
import styles from './UploadByLink.module.css';

type Result = { name: string; message: string };

async function sendOne(token: string, file: File, project: boolean): Promise<Result> {
  try {
    const receipt = await uploadLinks.upload(token, file);
    const message = project && receipt.duplicate ? `${receipt.filename}: Ya estaba en el proyecto` : `recibido: ${receipt.filename}`;
    return { name: file.name, message };
  } catch (e) {
    return { name: file.name, message: `${file.name}: ${(e as ApiError).message}` };
  }
}

function useSending(token: string, project: boolean) {
  const [results, setResults] = useState<Result[]>([]);
  const send = async (files: File[]) => {
    setResults(files.map((f) => ({ name: f.name, message: `enviando: ${f.name}` })));
    for (const [index, file] of files.entries()) {
      const result = await sendOne(token, file, project);
      setResults((all) => all.map((r, i) => (i === index ? result : r)));
    }
  };
  return { results, send };
}

function Facts({ info }: { info: S['UploadLinkInfo'] }) {
  return (
    <dl className={styles.facts}>
      {info.target === 'CASE' && <div><dt>Caso</dt><dd>{info.caseTitle}</dd></div>}
      <div><dt>Proyecto</dt><dd>{info.projectName}</dd></div>
      <div><dt>El enlace caduca</dt><dd>{formatDate(info.expiresAt)}</dd></div>
    </dl>
  );
}

const TITLES: Record<string, string> = { PROJECT: 'Subir documentos al proyecto', CASE: 'Subir documentos al caso' };

/** Public page: only the token travels in the URL, and nothing here lists or links to any document. */
export function UploadByLink({ token }: { token: string }) {
  const info = useLoad(() => uploadLinks.info(token), [token]);
  const { results, send } = useSending(token, info.data?.target === 'PROJECT');
  return (
    <main className={styles.page}>
      <h1>{TITLES[info.data?.target ?? ''] ?? 'Subir documentos'}</h1>
      <Notice message={info.error} />
      {info.data && (
        <>
          <Facts info={info.data} />
          <Field label="Elegir ficheros"><input type="file" multiple onChange={(e) => { send([...(e.target.files ?? [])]); e.target.value = ''; }} /></Field>
          <ul aria-label="Estado de los ficheros">{results.map((r, i) => <li key={`${r.name}-${i}`}>{r.message}</li>)}</ul>
        </>
      )}
    </main>
  );
}
