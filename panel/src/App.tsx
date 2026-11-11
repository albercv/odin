import { useEffect, useState } from 'react';
import { api, setUnauthorizedHandler, type S } from './api/client';
import { AppShell } from './layout/AppShell';
import { Cover } from './pages/Cover';
import { Login } from './pages/Login';
import { useBrand } from './theme/BrandContext';
import { ConfirmProvider } from './ui/ConfirmDialog';
import { ToastProvider } from './ui/Toast';

function useHash() {
  const [hash, setHash] = useState(location.hash);
  useEffect(() => {
    const sync = () => setHash(location.hash);
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);
  const clear = () => { history.replaceState(null, '', location.pathname + location.search); setHash(''); };
  return { hash, clear };
}

function Panel() {
  const [me, setMe] = useState<S['AdminMe'] | null | undefined>(undefined);
  const [notice, setNotice] = useState<string>();
  const { hash, clear } = useHash();
  const showCover = hash === '#portada' && Boolean(useBrand().cover);
  const refresh = () => api.me().then(setMe, () => setMe(null));
  useEffect(() => { setUnauthorizedHandler(() => { setNotice(undefined); setMe(null); }); }, []);
  useEffect(() => { if (!showCover) refresh(); }, [showCover]);
  if (showCover) return <Cover onStart={clear} />;
  if (me === undefined) return <p>Cargando…</p>;
  if (me === null) return <Login onLoggedIn={() => { setNotice(undefined); refresh(); }} notice={notice} />;
  const passwordChanged = () => { setNotice('Contraseña cambiada. Entra de nuevo.'); setMe(null); };
  const logout = () => api.logout().finally(() => { setNotice(undefined); setMe(null); });
  return <AppShell me={me} onLogout={logout} onPasswordChanged={passwordChanged} />;
}

export function App() {
  return <ToastProvider><ConfirmProvider><Panel /></ConfirmProvider></ToastProvider>;
}
