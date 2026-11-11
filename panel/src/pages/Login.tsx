import { useState } from 'react';
import { api } from '../api/client';
import { BrainCanvas } from '../brain/BrainCanvas';
import { LOGIN_PLACEMENT } from '../brain/brainRenderer';
import { Notice } from '../components/Notice';
import { useAction } from '../components/useLoad';
import { BrandMark } from '../layout/BrandMark';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import styles from './Login.module.css';

function LoginBrand() {
  return (
    <section className={styles.brand} aria-label="Odín">
      <BrainCanvas placement={LOGIN_PLACEMENT} />
      <div className={styles.shade} />
      <BrandMark showOwner />
      <div className={styles.claim}>
        <h1>Cómo se resolvió, y dónde consta.</h1>
        <p>Odín guarda los documentos y las fichas de la empresa y responde a la IA de cada persona citando la fuente.</p>
      </div>
    </section>
  );
}

export function Login({ onLoggedIn, notice }: { onLoggedIn: () => void; notice?: string }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.login({ email, password }))) onLoggedIn();
  };
  return (
    <div className={styles.login}>
      <LoginBrand />
      <div className={styles.formWrap}>
        <form onSubmit={submit} className={styles.form}>
          <h2>Entra en el panel</h2>
          {notice && <p role="status">{notice}</p>}
          <Notice message={error} />
          <Field label="Correo"><input type="email" required autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
          <Field label="Contraseña"><input type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
          <Button type="submit" variant="primary" className={styles.submit}>Entrar</Button>
          <p className={styles.help}>¿Has olvidado la contraseña? Pide a quien administra el grupo que te la restablezca.</p>
        </form>
      </div>
    </div>
  );
}
