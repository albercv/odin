import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { useToast } from '../ui/Toast';
import { Notice } from './Notice';
import { useProjectTerms } from './projectTerms';
import { useAction, useLoad } from './useLoad';

type Props = { companyId: string; open: boolean; onClose: () => void; onDone: () => void };
type Fields = { title: string; projectId: string; caseDocument: boolean };
type Failure = { name: string; message: string };

const NO_FIELDS: Fields = { title: '', projectId: '', caseDocument: false };
const baseName = (file: File) => file.name.replace(/\.[^.]+$/, '');

function summary(total: number, failed: Failure[]) {
  if (failed.length === 0) return `Se subieron ${total} documentos.`;
  const names = failed.map((f) => `${f.name} (${f.message})`).join('; ');
  return `Se subió ${total - failed.length} de ${total} documentos. Fallaron: ${names}.`;
}

/** Uploads one by one; with several files each title is its file name without the extension. */
async function uploadFiles(companyId: string, files: File[], fields: Fields): Promise<Failure[]> {
  const failed: Failure[] = [];
  for (const file of files) {
    const title = files.length > 1 ? baseName(file) : fields.title;
    try {
      await api.upload(companyId, file, title, fields.projectId, fields.caseDocument);
    } catch (e) {
      failed.push({ name: file.name, message: (e as Error).message });
    }
  }
  return failed;
}

type FormProps = { fields: Fields; several: boolean; projects: S['ProjectView'][]; error?: string; onFiles: (files: File[]) => void; onChange: (changes: Partial<Fields>) => void; onSubmit: (e: React.FormEvent) => void };

function UploadForm({ fields, several, projects, error, onFiles, onChange, onSubmit }: FormProps) {
  const t = useProjectTerms();
  return (
    <form className={form.form} onSubmit={onSubmit}>
      <Notice message={error} />
      <Field label="Fichero"><input type="file" multiple onChange={(e) => onFiles([...(e.target.files ?? [])])} /></Field>
      {!several && <Field label="Título (opcional)"><input value={fields.title} onChange={(e) => onChange({ title: e.target.value })} /></Field>}
      <label className={form.check}>
        <input type="checkbox" checked={fields.caseDocument} onChange={(e) => onChange({ caseDocument: e.target.checked })} />
        Son fichas de caso
      </label>
      <Field label={t.One}>
        <select value={fields.projectId} onChange={(e) => onChange({ projectId: e.target.value })}>
          <option value="">{t.noneOne}</option>
          {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </Field>
      <div><Button type="submit" variant="primary">Subir</Button></div>
    </form>
  );
}

export function UploadDialog({ companyId, open, onClose, onDone }: Props) {
  const projects = useLoad(() => api.projects(companyId), [companyId]);
  const toast = useToast();
  const [files, setFiles] = useState<File[]>([]);
  const [fields, setFields] = useState(NO_FIELDS);
  const { error, act } = useAction();
  const run = async () => {
    if (files.length === 0) throw new Error('Elige al menos un fichero.');
    const failed = await uploadFiles(companyId, files, fields);
    if (files.length > 1) toast(summary(files.length, failed));
    else if (failed[0]) throw new Error(failed[0].message);
  };
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(run))) return;
    setFields(NO_FIELDS);
    onClose();
    onDone();
  };
  return (
    <FormDialog title="Subir documento" open={open} onClose={onClose}>
      <UploadForm fields={fields} several={files.length > 1} projects={projects.data ?? []} error={error} onFiles={setFiles}
        onChange={(changes) => setFields({ ...fields, ...changes })} onSubmit={submit} />
    </FormDialog>
  );
}
