import { useState } from 'react';
import { api, type S } from '../api/client';
import { Capabilities } from '../components/Capabilities';
import { NameDialog } from '../components/NameDialog';
import { Notice } from '../components/Notice';
import { DEFAULT_LABEL } from '../components/projectTerms';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { PageHeader } from '../ui/PageHeader';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { StatusPill } from '../ui/StatusPill';
import { Table, Td, Tr, tableStyles } from '../ui/Table';

type Label = S['ProjectLabelView'];

function LabelFields({ label, onChange }: { label: Label; onChange: (label: Label) => void }) {
  return (
    <>
      <Field label="Nombre de los proyectos (singular)"><input required minLength={2} maxLength={30} value={label.singular} onChange={(e) => onChange({ ...label, singular: e.target.value })} /></Field>
      <Field label="Nombre de los proyectos (plural)"><input required minLength={2} maxLength={30} value={label.plural} onChange={(e) => onChange({ ...label, plural: e.target.value })} /></Field>
      <Field label="Artículo">
        <select value={label.feminine ? 'la' : 'el'} onChange={(e) => onChange({ ...label, feminine: e.target.value === 'la' })}>
          <option value="el">el</option>
          <option value="la">la</option>
        </select>
      </Field>
    </>
  );
}

const sameLabel = (a: Label, b: Label) => a.singular === b.singular && a.plural === b.plural && a.feminine === b.feminine;

type EditProps = { company: S['CompanyView']; onSaved: () => void; onCancel: () => void };

function EditForm({ company, onSaved, onCancel }: EditProps) {
  const [name, setName] = useState(company.name);
  const [code, setCode] = useState(company.code);
  const [label, setLabel] = useState(company.projectLabel ?? DEFAULT_LABEL);
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.updateCompany(company.id, { name, ...(code !== company.code && { code }), ...(!sameLabel(label, company.projectLabel ?? DEFAULT_LABEL) && { projectLabel: label }) }))) onSaved();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label={`Nombre de ${company.name}`}><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <Field label="Código"><input required pattern="[A-Z0-9]{2,6}" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} /></Field>
      <LabelFields label={label} onChange={setLabel} />
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

type Props = { onOpen: (id: string) => void; onChanged?: () => void };

export function Companies({ onOpen, onChanged }: Props) {
  const list = useLoad(api.companies, []);
  const [adding, setAdding] = useState(false);
  const changed = () => { list.reload(); onChanged?.(); };
  return (
    <div>
      <PageHeader title="Grupo" actions={<Button variant="primary" icon="plus" onClick={() => setAdding(true)}>Nueva empresa</Button>} />
      <Notice message={list.error} />
      <Section title="Empresas del grupo">
        <Table columns={['Empresa', 'Capacidades']}>
          {list.data?.map((c) => <CompanyRow key={c.id} c={c} onOpen={() => onOpen(c.id)} onDone={changed} />)}
        </Table>
      </Section>
      <NameDialog title="Nueva empresa" label="Nombre de la empresa" submitLabel="Crear empresa" codeLabel="Código" open={adding}
        onSubmit={api.createCompany} onClose={() => setAdding(false)} onDone={changed} />
    </div>
  );
}
