import { useState } from 'react';
import type { S } from '../api/client';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { FormDialog } from '../ui/FormDialog';
import { useAction } from './useLoad';

type Props = { title: string; topic?: S['TopicView']; onSubmit: (b: S['TopicRequest']) => Promise<unknown>; onClose: () => void; onDone: () => void };

/** The API's messages about the definition go under «Definición»; the rest (duplicates, limits) under «Nombre». */
const aboutDefinition = (message: string) => /definición/i.test(message);

export function TopicDialog({ title, topic, onSubmit, onClose, onDone }: Props) {
  const [name, setName] = useState(topic?.name ?? '');
  const [definition, setDefinition] = useState(topic?.definition ?? '');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(() => onSubmit({ name, definition })))) return;
    onClose();
    onDone();
  };
  return (
    <FormDialog title={title} open onClose={onClose}>
      <form className={form.form} onSubmit={submit}>
        <Field label="Nombre" error={error && !aboutDefinition(error) ? error : undefined}><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Definición" error={error && aboutDefinition(error) ? error : undefined}><textarea required value={definition} onChange={(e) => setDefinition(e.target.value)} /></Field>
        <div><Button type="submit" variant="primary">Guardar tema</Button></div>
      </form>
    </FormDialog>
  );
}
