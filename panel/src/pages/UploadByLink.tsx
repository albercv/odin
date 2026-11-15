import { useState } from 'react';
import { ApiError, uploadLinks, type S } from '../api/client';
import { Field } from '../ui/Field';
import { Notice } from '../components/Notice';
import { projectTerms, type ProjectTerms } from '../components/projectTerms';
import { useLoad } from '../components/useLoad';
import { formatDate } from './reviewNames';
import styles from './UploadByLink.module.css';

type Result = { name: string; message: string };

async function sendOne(token: string, file: File, already?: string): Promise<Result> {
  try {
    const receipt = await uploadLinks.upload(token, file);
    const message = already && receipt.duplicate ? `${receipt.filename}: ${already}` : `recibido: ${receipt.filename}`;
    return { name: file.name, message };
  } catch (e) {
    return { name: file.name, message: `${file.name}: ${(e as ApiError).message}` };
  }
}

function useSending(token: string, already?: string) {
  const [results, setResults] = useState<Result[]>([]);
  const send = async (files: File[]) => {
    setResults(files.map((f) => ({ name: f.name, message: `enviando: ${f.name}` })));
    for (const [index, file] of files.entries()) {
      const result = await sendOne(token, file, already);
      setResults((all) => all.map((r, i) => (i === index ? result : r)));
    }
  };
  return { results, send };
}

function Facts({ info, t }: { info: S['UploadLinkInfo']; t: ProjectTerms }) {
  return (
    <dl className={styles.facts}>
      {info.target === 'CASE' && <div><dt>Caso</dt><dd>{info.caseTitle}</dd></div>}
      <div><dt>{t.One}</dt><dd>{info.projectName}</dd></div>
      <div><dt>El enlace caduca</dt><dd>{formatDate(info.expiresAt)}</dd></div>
    </dl>
  );
}

const title = (target: string | undefined, t: ProjectTerms) =>
  target === 'PROJECT' ? `Subir documentos ${t.agree('al', 'a la')} ${t.one}` : target === 'CASE' ? 'Subir documentos al caso' : 'Subir documentos';

/** Public page: only the token travels in the URL, and nothing here lists or links to any document. */
export function UploadByLink({ token }: { token: string }) {
  const info = useLoad(() => uploadLinks.info(token), [token]);
  const t = projectTerms(info.data?.projectLabel);
  const { results, send } = useSending(token, info.data?.target === 'PROJECT' ? `Ya estaba ${t.agree('en el', 'en la')} ${t.one}` : undefined);
  return (
    <main className={styles.page}>
      <h1>{title(info.data?.target, t)}</h1>
      <Notice message={info.error} />
      {info.data && (
        <>
          <Facts info={info.data} t={t} />
          <Field label="Elegir ficheros"><input type="file" multiple onChange={(e) => { send([...(e.target.files ?? [])]); e.target.value = ''; }} /></Field>
          <ul aria-label="Estado de los ficheros">{results.map((r, i) => <li key={`${r.name}-${i}`}>{r.message}</li>)}</ul>
        </>
      )}
    </main>
  );
}
