import { useEffect, useState } from 'react';
import { api, setUnauthorizedHandler, type S } from './api/client';
import { Account } from './pages/Account';
import { Companies } from './pages/Companies';
import { Company } from './pages/Company';
import { Documents } from './pages/Documents';
import { Login } from './pages/Login';
import { Review } from './pages/Review';

type Page = 'companies' | 'company' | 'documents' | 'review' | 'account';

function Nav({ isGroup, name, page, setPage, onLogout }: {
  isGroup: boolean; name: string; page: Page; setPage: (p: Page) => void; onLogout: () => void;
}) {
  const link = (p: Page, text: string) => (
    <a href="#" aria-current={page === p ? 'page' : undefined} onClick={(e) => { e.preventDefault(); setPage(p); }}>{text}</a>
  );
  return (
    <nav aria-label="Principal">
      {isGroup && link('companies', 'Empresas')}
      {link('company', 'Empresa')}
      {link('documents', 'Documentos')}
      {link('review', 'Revisión')}
      {link('account', `Mi cuenta (${name})`)}
      <button type="button" onClick={onLogout}>Salir</button>
    </nav>
  );
}

function Shell({ me, onLogout, onPasswordChanged }: { me: S['AdminMe']; onLogout: () => void; onPasswordChanged: () => void }) {
  const isGroup = me.role === 'GROUP_ADMIN';
  const [page, setPage] = useState<Page>(isGroup ? 'companies' : 'company');
  const [companyId, setCompanyId] = useState(me.companyId ?? '');
  const open = (id: string) => { setCompanyId(id); setPage('company'); };
  const needsCompany = page !== 'companies' && page !== 'account';
  return (
    <>
      <Nav isGroup={isGroup} name={me.displayName} page={page} setPage={setPage} onLogout={onLogout} />
      <main>
        {page === 'account' && <Account me={me} onPasswordChanged={onPasswordChanged} />}
        {page === 'companies' && <Companies onOpen={open} />}
        {needsCompany && !companyId && <p>Elige primero una empresa en «Empresas».</p>}
        {needsCompany && companyId && page === 'company' && <Company key={companyId} companyId={companyId} me={me} />}
        {needsCompany && companyId && page === 'documents' && <Documents key={companyId} companyId={companyId} />}
        {needsCompany && companyId && page === 'review' && <Review key={companyId} companyId={companyId} />}
      </main>
    </>
  );
}

export function App() {
  const [me, setMe] = useState<S['AdminMe'] | null | undefined>(undefined);
  const [notice, setNotice] = useState<string>();
  const refresh = () => api.me().then(setMe, () => setMe(null));
  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null));
    refresh();
  }, []);
  if (me === undefined) return <p>Cargando…</p>;
  if (me === null) return <Login onLoggedIn={refresh} notice={notice} />;
  const passwordChanged = () => { setNotice('Contraseña cambiada. Entra de nuevo.'); setMe(null); };
  return <Shell me={me} onLogout={() => api.logout().finally(() => setMe(null))} onPasswordChanged={passwordChanged} />;
}
