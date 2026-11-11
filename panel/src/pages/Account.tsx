import { useState } from 'react';
import { api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction } from '../components/useLoad';
import type { ColorPreference } from '../theme/colorPreference';
import { useColorChoice } from '../theme/ColorModeContext';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import form from '../ui/Form.module.css';
import { useToast } from '../ui/Toast';
import { PageHeader } from '../ui/PageHeader';
import { Section } from '../ui/Section';
import styles from './Account.module.css';

const THEMES: [ColorPreference, string][] = [['system', 'Como el sistema'], ['light', 'Claro'], ['dark', 'Oscuro']];

function ThemeChoice() {
  const { preference, setPreference } = useColorChoice();
  return (
    <Section title="Tema">
      <div role="radiogroup" aria-label="Tema" className={styles.themes}>
        {THEMES.map(([value, label]) => (
          <label key={value} className={`${styles.theme} ${preference === value ? styles.selected : ''}`}>
            <input type="radio" name="theme" checked={preference === value} onChange={() => setPreference(value)} />
            {label}
          </label>
        ))}
      </div>
    </Section>
  );
}

function NamesForm({ me, onSaved }: { me: S['AdminMe']; onSaved: () => void }) {
  const [firstName, setFirstName] = useState(me.firstName);
  const [lastName, setLastName] = useState(me.lastName ?? '');
  const { error, act } = useAction();
  const toast = useToast();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(() => api.updateUser(me.userId, { firstName, lastName })))) return;
    toast('Datos guardados');
    onSaved();
  };
  return (
    <Section title="Mis datos">
      <form className={`${form.form} ${styles.password}`} onSubmit={submit}>
        <Notice message={error} />
        <Field label="Nombre"><input required value={firstName} onChange={(e) => setFirstName(e.target.value)} /></Field>
        <Field label="Apellidos"><input required value={lastName} onChange={(e) => setLastName(e.target.value)} /></Field>
        <div><Button type="submit" variant="primary">Guardar mis datos</Button></div>
      </form>
    </Section>
  );
}

function PasswordForm({ me, onPasswordChanged }: { me: S['AdminMe']; onPasswordChanged: () => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.setPassword(me.userId, { currentPassword: current, newPassword: next }))) onPasswordChanged();
  };
  return (
    <Section title="Contraseña">
      <form className={`${form.form} ${styles.password}`} onSubmit={submit}>
        <Notice message={error} />
        <Field label="Contraseña actual"><input type="password" required value={current} onChange={(e) => setCurrent(e.target.value)} /></Field>
        <Field label="Contraseña nueva"><input type="password" required minLength={12} value={next} onChange={(e) => setNext(e.target.value)} /></Field>
        <div><Button type="submit" variant="primary">Cambiar contraseña</Button></div>
      </form>
    </Section>
  );
}

type AccountProps = { me: S['AdminMe']; onPasswordChanged: () => void; onProfileChanged: () => void };

export function Account({ me, onPasswordChanged, onProfileChanged }: AccountProps) {
  return (
    <div>
      <PageHeader title="Mi cuenta" description={me.displayName} />
      <NamesForm me={me} onSaved={onProfileChanged} />
      <PasswordForm me={me} onPasswordChanged={onPasswordChanged} />
      <ThemeChoice />
    </div>
  );
}
