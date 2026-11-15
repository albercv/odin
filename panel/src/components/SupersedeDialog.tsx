import { useState } from 'react';
import { api, type S } from '../api/client';
import { formatDate, projectName } from '../pages/reviewNames';
import { Button } from '../ui/Button';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { SearchBox } from '../ui/SearchBox';
import { Notice } from './Notice';
import { useProjectTerms } from './projectTerms';
import { useAction } from './useLoad';
import { useServerPage } from './useServerPage';

type Props = { document: S['DocumentView']; projects: S['ProjectView'][]; onClose: () => void; onDone: () => void };

const SHOWN = 10;
const FETCHED = 50;
const HELP = 'Usa esta marca cuando un documento sustituye entero a otro, como una póliza renovada. Un anexo que cambia una parte no sustituye al contrato.';

function Candidates({ candidates, hint, empty, projects, chosen, onChoose }: { hint: boolean; empty: boolean; candidates: S['DocumentView'][]; projects: S['ProjectView'][]; chosen?: S['DocumentView']; onChoose: (d: S['DocumentView']) => void }) {
  const t = useProjectTerms();
  return (
    <fieldset className={form.group}>
      <legend>Documento que lo sustituye</legend>
      {empty && <p className={form.hint}>Ningún documento listo coincide con la búsqueda.</p>}
      {hint && <p className={form.hint}>Escribe al menos 3 letras del título para afinar</p>}
      {candidates.map((d) => (
        <label key={d.id} className={form.check}>
          <input type="radio" name="replacement" checked={chosen?.id === d.id} onChange={() => onChoose(d)} />
          {`${d.title} · ${projectName(d.projectId, projects, t.noneOne)} · ${formatDate(d.uploadedAt)}`}
        </label>
      ))}
    </fieldset>
  );
}

function useCandidates(old: S['DocumentView']) {
  const found = useServerPage<S['DocumentView']>(
    async (q, offset, limit) => {
      const r = await api.documentPage(old.companyId, { q, offset, limit });
      return { items: r.documents, total: r.total };
    }, [old.companyId], FETCHED);
  const candidates = found.items.filter((d) => d.status === 'READY' && d.id !== old.id).slice(0, SHOWN);
  const ready = found.items.filter((d) => d.status === 'READY' && d.id !== old.id).length;
  // La pista solo si hay candidatos listos que no se ven (más en el servidor o más de SHOWN) y no se está buscando ya.
  const hidden = found.items.length < found.total || ready > candidates.length;
  return { found, candidates, hint: !found.loading && !found.error && !found.query && hidden };
}

export function SupersedeDialog({ document: old, projects, onClose, onDone }: Props) {
  const [chosen, setChosen] = useState<S['DocumentView']>();
  const { error, act } = useAction();
  const { found, candidates, hint } = useCandidates(old);
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
        <SearchBox label="Buscar por título" onSearch={found.setQuery} />
        <Notice message={found.error} />
        <Candidates candidates={candidates} hint={hint} empty={!found.loading && !found.error && candidates.length === 0} projects={projects} chosen={chosen} onChoose={setChosen} />
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
