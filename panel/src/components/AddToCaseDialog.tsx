import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';

type Props = { companyId: string; caseId: string; projectId?: string; inFile: Set<string>; onClose: () => void; onDone: () => void };

function Candidates({ candidates, chosen, onToggle }: { candidates: S['DocumentView'][]; chosen: string[]; onToggle: (id: string) => void }) {
  return (
    <fieldset className={form.group}>
      <legend>Documentos del proyecto y generales</legend>
      {candidates.length === 0 && <p className={form.hint}>Ningún documento coincide.</p>}
      {candidates.map((d) => (
        <label key={d.id} className={form.check}>
          <input type="checkbox" aria-label={d.title} checked={chosen.includes(d.id)} onChange={() => onToggle(d.id)} />
          {d.title}
        </label>
      ))}
    </fieldset>
  );
}

/** Only the case's project and the general documents (no project) can join the file. */
export function AddToCaseDialog({ companyId, caseId, projectId, inFile, onClose, onDone }: Props) {
  const all = useLoad(() => api.documents(companyId), [companyId]);
  const [search, setSearch] = useState('');
  const [chosen, setChosen] = useState<string[]>([]);
  const { error, act } = useAction();
  const candidates = (all.data?.documents ?? []).filter((d: S['DocumentView']) => d.id !== caseId && !inFile.has(d.id)
    && (!d.projectId || d.projectId === projectId) && d.title.toLowerCase().includes(search.toLowerCase()));
  const toggle = (id: string) => setChosen((c) => (c.includes(id) ? c.filter((x) => x !== id) : [...c, id]));
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.addToCase(companyId, caseId, chosen))) { onClose(); onDone(); }
  };
  return (
    <FormDialog title="Añadir documentos" open onClose={onClose}>
      <form className={form.form} onSubmit={submit}>
        <Notice message={error ?? all.error} />
        <Field label="Buscar por título"><input value={search} onChange={(e) => setSearch(e.target.value)} /></Field>
        <Candidates candidates={candidates} chosen={chosen} onToggle={toggle} />
        <div><Button type="submit" variant="primary" disabled={chosen.length === 0}>Añadir</Button></div>
      </form>
    </FormDialog>
  );
}
