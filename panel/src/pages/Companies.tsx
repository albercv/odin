import { useState } from 'react';
import { api, type S } from '../api/client';
import { Capabilities } from '../components/Capabilities';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import form from '../ui/Form.module.css';
import { Field } from '../ui/Field';
import { FormDialog } from '../ui/FormDialog';
import { PageHeader } from '../ui/PageHeader';
import { RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { Table, tableStyles } from '../ui/Table';

function RenameForm({ c, onSave, onCancel }: { c: S['CompanyView']; onSave: (name: string) => void; onCancel: () => void }) {
  const [name, setName] = useState(c.name);
  return (
    <form className={form.form} onSubmit={(e) => { e.preventDefault(); onSave(name); }}>
      <Field label={`Nombre de ${c.name}`}><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <div className={form.buttons}>
        <Button type="submit" variant="primary">Guardar {c.name}</Button>
        <Button onClick={onCancel}>Cancelar {c.name}</Button>
      </div>
    </form>
  );
}

function CompanyRow({ c, onOpen, onDone }: { c: S['CompanyView']; onOpen: () => void; onDone: () => void }) {
  const [renaming, setRenaming] = useState(false);
  const { error, act } = useAction();
  const run = async (fn: () => Promise<unknown>) => (await act(fn)) && onDone();
  const rename = async (name: string) => { if (await act(() => api.renameCompany(c.id, name))) { setRenaming(false); onDone(); } };
  return (
    <tr>
      <td>
        {renaming ? <RenameForm c={c} onSave={rename} onCancel={() => setRenaming(false)} /> : <strong>{c.name}</strong>}
        <Notice message={error} />
      </td>
      <td><Capabilities company={c} onChange={(caps) => run(() => api.setCapabilities(c.id, caps))} /></td>
      <td className={tableStyles.actions}>
        <Button onClick={onOpen}>Abrir {c.name}</Button>
        <RowActions label={`Más acciones de ${c.name}`}><Button variant="ghost" onClick={() => setRenaming(true)}>Renombrar {c.name}</Button></RowActions>
      </td>
    </tr>
  );
}

function NewCompanyDialog({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const [name, setName] = useState('');
  const { error, act } = useAction();
  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(() => api.createCompany(name)))) return;
    setName('');
    onClose();
    onDone();
  };
  return (
    <FormDialog title="Nueva empresa" open={open} onClose={onClose}>
      <form className={form.form} onSubmit={create}>
        <Notice message={error} />
        <Field label="Nombre de la empresa"><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <div><Button type="submit" variant="primary">Crear empresa</Button></div>
      </form>
    </FormDialog>
  );
}

export function Companies({ onOpen }: { onOpen: (id: string, name: string) => void }) {
  const list = useLoad(api.companies, []);
  const [adding, setAdding] = useState(false);
  return (
    <div>
      <PageHeader title="Empresas" actions={<Button variant="primary" icon="plus" onClick={() => setAdding(true)}>Nueva empresa</Button>} />
      <Notice message={list.error} />
      <Section title="Empresas del grupo">
        <Table columns={['Empresa', 'Capacidades']}>
          {list.data?.map((c) => <CompanyRow key={c.id} c={c} onOpen={() => onOpen(c.id, c.name)} onDone={list.reload} />)}
        </Table>
      </Section>
      <NewCompanyDialog open={adding} onClose={() => setAdding(false)} onDone={list.reload} />
    </div>
  );
}
