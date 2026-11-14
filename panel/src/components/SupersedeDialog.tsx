import { useState } from 'react';
import { api, type S } from '../api/client';
import { formatDate, projectName } from '../pages/reviewNames';
import { Button } from '../ui/Button';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { SearchBox } from '../ui/SearchBox';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';

type Props = { document: S['DocumentView']; projects: S['ProjectView'][]; onClose: () => void; onDone: () => void };

const CANDIDATES = 10;
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

export function SupersedeDialog({ document: old, projects, onClose, onDone }: Props) {
  const [search, setSearch] = useState('');
  const [chosen, setChosen] = useState<S['DocumentView']>();
  const { error, act } = useAction();
  const found = useLoad(() => api.documentPage(old.companyId, { q: search, limit: CANDIDATES }), [old.companyId, search]);
  const candidates = (found.data?.documents ?? []).filter((d) => d.status === 'READY' && d.id !== old.id);
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
        <SearchBox label="Buscar por título" onSearch={setSearch} />
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
