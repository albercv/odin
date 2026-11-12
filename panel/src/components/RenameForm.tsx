import { useState } from 'react';
import { RowAction } from '../ui/RowActions';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { Notice } from './Notice';
import { useAction } from './useLoad';

type Props = { name: string; label: string; onSave: (name: string) => Promise<unknown>; onSaved: () => void; onCancel: () => void };

/** Inline rename; buttons read «Guardar <name>» and «Cancelar <name>». */
export function RenameForm({ name, label, onSave, onSaved, onCancel }: Props) {
  const [value, setValue] = useState(name);
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => onSave(value))) onSaved();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label={label}><input required value={value} onChange={(e) => setValue(e.target.value)} /></Field>
      <div className={form.buttons}>
        <RowAction type="submit" variant="primary" text="Guardar" target={name} />
        <RowAction text="Cancelar" target={name} onClick={onCancel} />
      </div>
    </form>
  );
}
