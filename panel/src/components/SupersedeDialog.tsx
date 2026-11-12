import { useState } from 'react';
import { api, type S } from '../api/client';
import { formatDate, projectName } from '../pages/reviewNames';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { Notice } from './Notice';
import { useAction } from './useLoad';

type Props = { document: S['DocumentView']; documents: S['DocumentView'][]; projects: S['ProjectView'][]; onClose: () => void; onDone: () => void };

const HELP = 'Usa esta marca cuando un documento sustituye entero a otro, como una póliza renovada. Un anexo que cambia una parte no sustituye al contrato.';

function Candidates({ candidates, projects, chosen, onChoose }: { candidates: S['DocumentView'][]; projects: S['ProjectView'][]; chosen?: S['DocumentView']; onChoose: (d: S['DocumentView']) => void }) {
  return (
    <fieldset className={form.group}>
      <legend>Documento que lo sustituye</legend>
      {candidates.length === 0 && <p className={form.hint}>Ningún documento listo coincide con la búsqueda.</p>}
      {candidates.map((d) => (
        <label key={d.id} className={form.check}>
          <input type="radio" name="replacement" checked={chosen?.id === d.id} onChange={() => onChoose(d)} />
          {`${d.title} · ${projectName(d.projectId, projects)} · ${formatDate(d.uploadedAt)}`}
        </label>
      ))}
    </fieldset>
  );
}

export function SupersedeDialog({ document: old, documents, projects, onClose, onDone }: Props) {
  const [search, setSearch] = useState('');
  const [chosen, setChosen] = useState<S['DocumentView']>();
  const { error, act } = useAction();
  const candidates = documents.filter((d) => d.status === 'READY' && d.id !== old.id && d.title.toLowerCase().includes(search.toLowerCase()));
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (chosen && (await act(() => api.supersede(old.id, chosen.id)))) {
      onClose();
      onDone();
    }
  };
  return (
    <FormDialog title="Marcar como sustituido" open onClose={onClose}>
      <form className={form.form} onSubmit={submit}>
        <p className={form.hint}>{HELP}</p>
        <Notice message={error} />
        <Field label="Buscar por título"><input value={search} onChange={(e) => setSearch(e.target.value)} /></Field>
        <Candidates candidates={candidates} projects={projects} chosen={chosen} onChoose={setChosen} />
        {chosen && (
          <>
            <p>{`“${old.title}” quedará guardado, pero Odín lo dará como sustituido por “${chosen.title}” y lo pondrá detrás en las búsquedas.`}</p>
            <div><Button type="submit" variant="primary">Marcar como sustituido</Button></div>
          </>
        )}
      </form>
    </FormDialog>
  );
}
