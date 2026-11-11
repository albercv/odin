import { useState } from 'react';
import { api } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction } from '../components/useLoad';

export function Login({ onLoggedIn, notice }: { onLoggedIn: () => void; notice?: string }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.login({ email, password }))) onLoggedIn();
  };
  return (
    <form onSubmit={submit} className="card">
      <h1>Odín · Entrada</h1>
      {notice && <p role="status">{notice}</p>}
      <Notice message={error} />
      <label>Correo <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      <label>Contraseña <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} /></label>
      <button type="submit">Entrar</button>
    </form>
  );
}
