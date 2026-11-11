import { useState } from 'react';
import { api, type S } from '../api/client';
import { Capabilities } from '../components/Capabilities';
import { NameDialog } from '../components/NameDialog';
import { Notice } from '../components/Notice';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { PageHeader } from '../ui/PageHeader';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { Table, tableStyles } from '../ui/Table';

type EditProps = { company: S['CompanyView']; canEditCode: boolean; onSaved: () => void; onCancel: () => void };

function EditForm({ company, canEditCode, onSaved, onCancel }: EditProps) {
  const [name, setName] = useState(company.name);
  const [code, setCode] = useState(company.code);
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.updateCompany(company.id, { name, ...(canEditCode && code !== company.code && { code }) }))) onSaved();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label={`Nombre de ${company.name}`}><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
      {canEditCode && <Field label="Código"><input required pattern="[A-Z0-9]{2,6}" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} /></Field>}
      <div className={form.buttons}>
        <Button type="submit" variant="primary">Guardar {company.name}</Button>
        <Button onClick={onCancel}>Cancelar {company.name}</Button>
      </div>
    </form>
  );
}

type RowProps = { c: S['CompanyView']; canEditCode: boolean; onOpen: () => void; onDone: () => void };

function CompanyRow({ c, canEditCode, onOpen, onDone }: RowProps) {
  const [renaming, setRenaming] = useState(false);
  const { error, act } = useAction();
  const run = async (fn: () => Promise<unknown>) => (await act(fn)) && onDone();
  return (
    <tr>
      <td className={tableStyles.person} data-label="Empresa">
        {renaming
          ? <EditForm company={c} canEditCode={canEditCode} onSaved={() => { setRenaming(false); onDone(); }} onCancel={() => setRenaming(false)} />
          : <><strong>{c.name}</strong><span>Código {c.code}</span></>}
        <Notice message={error} />
      </td>
      <td data-label="Capacidades"><Capabilities company={c} onChange={(caps) => run(() => api.setCapabilities(c.id, caps))} /></td>
      <td className={tableStyles.actions}>
        <RowAction text="Abrir" target={c.name} onClick={onOpen} />
        <RowActions label={`Más acciones de ${c.name}`}><RowAction variant="ghost" text="Editar" target={c.name} onClick={() => setRenaming(true)} /></RowActions>
      </td>
    </tr>
  );
}

export function Companies({ onOpen, canEditCode }: { onOpen: (id: string, name: string) => void; canEditCode: boolean }) {
  const list = useLoad(api.companies, []);
  const [adding, setAdding] = useState(false);
  return (
    <div>
      <PageHeader title="Empresas" actions={<Button variant="primary" icon="plus" onClick={() => setAdding(true)}>Nueva empresa</Button>} />
      <Notice message={list.error} />
      <Section title="Empresas del grupo">
        <Table columns={['Empresa', 'Capacidades']}>
          {list.data?.map((c) => <CompanyRow key={c.id} c={c} canEditCode={canEditCode} onOpen={() => onOpen(c.id, c.name)} onDone={list.reload} />)}
        </Table>
      </Section>
      <NameDialog title="Nueva empresa" label="Nombre de la empresa" submitLabel="Crear empresa" codeLabel="Código" open={adding}
        onSubmit={api.createCompany} onClose={() => setAdding(false)} onDone={list.reload} />
    </div>
  );
}
