import { useState } from 'react';
import { api, type Capability, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';

const CAPABILITIES = [
  ['SEARCH_KNOWLEDGE', 'Buscar conocimiento'],
  ['SAVE_KNOWLEDGE_CARD', 'Guardar fichas'],
] as const;

function Capabilities({ c, onChange }: { c: S['CompanyView']; onChange: (caps: Capability[]) => void }) {
  const has: Capability[] = c.capabilities;
  const toggle = (cap: Capability) => onChange(has.includes(cap) ? has.filter((x) => x !== cap) : [...has, cap]);
  return (
    <fieldset>
      <legend>Capacidades de {c.name}</legend>
      {CAPABILITIES.map(([cap, text]) => (
        <label key={cap}><input type="checkbox" checked={has.includes(cap)} onChange={() => toggle(cap)} /> {text}</label>
      ))}
    </fieldset>
  );
}

function Rename({ c, onSave }: { c: S['CompanyView']; onSave: (name: string) => void }) {
  const [name, setName] = useState(c.name);
  return (
    <form onSubmit={(e) => { e.preventDefault(); onSave(name); }}>
      <label>Nombre de {c.name} <input required value={name} onChange={(e) => setName(e.target.value)} /></label>
      <button type="submit">Renombrar</button>
    </form>
  );
}

function CompanyRow({ c, onOpen, onDone }: { c: S['CompanyView']; onOpen: () => void; onDone: () => void }) {
  const { error, act } = useAction();
  const run = async (fn: () => Promise<unknown>) => (await act(fn)) && onDone();
  return (
    <li className="card">
      <h2>{c.name}</h2>
      <Notice message={error} />
      <button type="button" onClick={onOpen}>Abrir {c.name}</button>
      <Rename c={c} onSave={(n) => run(() => api.renameCompany(c.id, n))} />
      <Capabilities c={c} onChange={(caps) => run(() => api.setCapabilities(c.id, caps))} />
    </li>
  );
}

function NewCompany({ onDone }: { onDone: () => void }) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const { error, act } = useAction();
  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.createCompany(name))) {
      setName('');
      setAdding(false);
      onDone();
    }
  };
  if (!adding) return <button type="button" onClick={() => setAdding(true)}>Nueva empresa</button>;
  return (
    <form onSubmit={create}>
      <Notice message={error} />
      <label>Nombre de la empresa <input required autoFocus value={name} onChange={(e) => setName(e.target.value)} /></label>
      <button type="submit">Crear empresa</button>
    </form>
  );
}

export function Companies({ onOpen }: { onOpen: (id: string) => void }) {
  const list = useLoad(api.companies, []);
  return (
    <section>
      <h1>Empresas</h1>
      <Notice message={list.error} />
      <NewCompany onDone={list.reload} />
      <ul>{list.data?.map((c) => <CompanyRow key={c.id} c={c} onOpen={() => onOpen(c.id)} onDone={list.reload} />)}</ul>
    </section>
  );
}
