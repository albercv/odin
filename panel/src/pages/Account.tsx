import { useState } from 'react';
import { api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { useAction } from '../components/useLoad';

export function Account({ me, onPasswordChanged }: { me: S['AdminMe']; onPasswordChanged: () => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.setPassword(me.userId, { currentPassword: current, newPassword: next }))) onPasswordChanged();
  };
  return (
    <div>
      <h1>Mi cuenta</h1>
      <p>{me.displayName}</p>
      <form onSubmit={submit}>
        <Notice message={error} />
        <label>Contraseña actual <input type="password" required value={current} onChange={(e) => setCurrent(e.target.value)} /></label>
        <label>Contraseña nueva <input type="password" required minLength={12} value={next} onChange={(e) => setNext(e.target.value)} /></label>
        <button type="submit">Cambiar contraseña</button>
      </form>
    </div>
  );
}
