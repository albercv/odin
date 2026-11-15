import { useEffect, useMemo, useState } from 'react';
import { api, setUnauthorizedHandler, type S } from './api/client';
import { AppShell } from './layout/AppShell';
import { Cover } from './pages/Cover';
import { Login } from './pages/Login';
import { UploadByLink } from './pages/UploadByLink';
import { useBrand } from './theme/BrandContext';
import { ColorModeContext } from './theme/ColorModeContext';
import { useColorMode } from './theme/useColorMode';
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
  const hasCover = Boolean(useBrand().cover);
  const showCover = hash === '#cover' && hasCover;
  const refresh = () => api.me().then(setMe, () => setMe(null));
  useEffect(() => { setUnauthorizedHandler(() => { setNotice(undefined); setMe(null); }); }, []);
  useEffect(() => { if (!showCover) refresh(); }, [showCover]);
  if (showCover) return <Cover onStart={clear} />;
  if (me === undefined) return <p>Cargando…</p>;
  if (me === null) return <Login onLoggedIn={() => { setNotice(undefined); refresh(); }} notice={notice} />;
  const passwordChanged = () => { setNotice('Contraseña cambiada. Entra de nuevo.'); setMe(null); };
  const logout = () => api.logout().finally(() => { setNotice(undefined); setMe(null); });
  return <AppShell me={me} onLogout={logout} onPasswordChanged={passwordChanged} onProfileChanged={refresh} />;
}

function ColorModeRoot({ children }: { children: React.ReactNode }) {
  const { preference, setPreference } = useColorMode();
  const value = useMemo(() => ({ preference, setPreference }), [preference, setPreference]);
  return <ColorModeContext.Provider value={value}>{children}</ColorModeContext.Provider>;
}

/** `/subir/:token` is public: it never loads the session. */
const uploadToken = () => /^\/subir\/([^/]+)$/.exec(location.pathname)?.[1];

export function App() {
  const token = uploadToken();
  return <ColorModeRoot><ToastProvider><ConfirmProvider>{token ? <UploadByLink token={token} /> : <Panel />}</ConfirmProvider></ToastProvider></ColorModeRoot>;
}
