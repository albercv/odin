import { useState } from 'react';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import styles from './AddPicker.module.css';

type Option = { id: string; name: string };
type Props = { label: string; placeholder: string; options: Option[]; onAdd: (id: string) => Promise<unknown> };

/** Pick one option and press «Añadir»; the choice clears once `onAdd` resolves truthy. */
export function AddPicker({ label, placeholder, options, onAdd }: Props) {
  const [chosen, setChosen] = useState('');
  const add = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await onAdd(chosen)) setChosen('');
  };
  return (
    <form className={styles.add} onSubmit={add}>
      <Field label={label}>
        <select value={chosen} onChange={(e) => setChosen(e.target.value)}>
          <option value="">{placeholder}</option>
          {options.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
        </select>
      </Field>
      <Button type="submit" disabled={!chosen}>Añadir</Button>
    </form>
  );
}
