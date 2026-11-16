import { useEffect, useState } from 'react';
import { onApiSuccess, type S } from '../api/client';
import { Button } from '../ui/Button';
import styles from './ExpiryBanner.module.css';

export const WARNING_MS = 5 * 60 * 1000;
export const IDLE_MS = 30 * 60 * 1000;

const time = (at: number) => new Date(at).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });

/** When the session ends if nothing else happens: the idle limit from the last good call, or the absolute one. */
export const expiryAt = (lastCall: number, absolute: number) => Math.min(lastCall + IDLE_MS, absolute);

/** Local deadline tracking: `me` gives the absolute limit; every successful API call slides the idle one. */
function useDeadline(me: S['AdminMe']) {
  const absolute = Date.parse(me.absoluteExpiresAt ?? '') || Infinity;
  const [lastCall, setLastCall] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => onApiSuccess(() => setLastCall(Date.now())), []);
  useEffect(() => {
    setLastCall(Date.now());
  }, [me]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(timer);
  }, []);
  const at = expiryAt(lastCall, absolute);
  return { at, absoluteIsCloser: absolute <= lastCall + IDLE_MS, soon: now >= at - WARNING_MS };
}

/** `onRefresh` asks «who am I» again: that call slides the idle timer and brings the new deadlines. */
export function ExpiryBanner({ me, onRefresh }: { me: S['AdminMe']; onRefresh: () => void }) {
  const { at, absoluteIsCloser, soon } = useDeadline(me);
  if (!soon || !Number.isFinite(at)) return null;
  return (
    <div role="alert" className={styles.banner}>
      <p>Tu sesión caduca a las {time(at)}.</p>
      {absoluteIsCloser
        ? <p>La sesión dura como máximo 12 horas: vuelve a entrar.</p>
        : <Button onClick={onRefresh}>Seguir conectado</Button>}
    </div>
  );
}
