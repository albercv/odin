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
import { StatusPill } from '../ui/StatusPill';
import { Table, Td, Tr, tableStyles } from '../ui/Table';

type EditProps = { company: S['CompanyView']; onSaved: () => void; onCancel: () => void };

function EditForm({ company, onSaved, onCancel }: EditProps) {
  const [name, setName] = useState(company.name);
  const [code, setCode] = useState(company.code);
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.updateCompany(company.id, { name, ...(code !== company.code && { code }) }))) onSaved();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label={`Nombre de ${company.name}`}><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <Field label="Código"><input required pattern="[A-Z0-9]{2,6}" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} /></Field>
      <div className={form.buttons}>
        <RowAction type="submit" variant="primary" text="Guardar" target={company.name} />
        <RowAction text="Cancelar" target={company.name} onClick={onCancel} />
      </div>
    </form>
  );
}

type RowProps = { c: S['CompanyView']; onOpen: () => void; onDone: () => void };

function CompanyRow({ c, onOpen, onDone }: RowProps) {
  const [renaming, setRenaming] = useState(false);
  const { error, act } = useAction();
  const run = async (fn: () => Promise<unknown>) => (await act(fn)) && onDone();
  return (
    <Tr>
      <Td label="Empresa" className={tableStyles.person}>
        {renaming
          ? <EditForm company={c} onSaved={() => { setRenaming(false); onDone(); }} onCancel={() => setRenaming(false)} />
          : <><strong>{c.name}</strong><span>Código {c.code} {!c.codeReviewed && <StatusPill tone="pending">Código por revisar</StatusPill>}</span></>}
        <Notice message={error} />
      </Td>
      <Td label="Capacidades"><Capabilities company={c} onChange={(caps) => run(() => api.setCapabilities(c.id, caps))} /></Td>
      <Td className={tableStyles.actions}>
        <RowAction text="Abrir" target={c.name} onClick={onOpen} />
        <RowActions label={`Más acciones de ${c.name}`}><RowAction variant="ghost" text="Editar" target={c.name} onClick={() => setRenaming(true)} /></RowActions>
      </Td>
    </Tr>
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
      <NameDialog title="Nueva empresa" label="Nombre de la empresa" submitLabel="Crear empresa" codeLabel="Código" open={adding}
        onSubmit={api.createCompany} onClose={() => setAdding(false)} onDone={list.reload} />
    </div>
  );
}
