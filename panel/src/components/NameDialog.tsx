import { useState } from 'react';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { Notice } from './Notice';
import { useAction } from './useLoad';

type Props = {
  title: string; label: string; submitLabel: string; open: boolean;
  onSubmit: (name: string) => Promise<unknown>; onClose: () => void; onDone: () => void;
};

export function NameDialog({ title, label, submitLabel, open, onSubmit, onClose, onDone }: Props) {
  const [name, setName] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(() => onSubmit(name)))) return;
    setName('');
    onClose();
    onDone();
  };
  return (
    <FormDialog title={title} open={open} onClose={onClose}>
      <form className={form.form} onSubmit={submit}>
        <Notice message={error} />
        <Field label={label}><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <div><Button type="submit" variant="primary">{submitLabel}</Button></div>
      </form>
    </FormDialog>
  );
}
