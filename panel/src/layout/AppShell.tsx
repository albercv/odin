import { useEffect, useState } from 'react';
import { api, type S } from '../api/client';
import { Account } from '../pages/Account';
import { Companies } from '../pages/Companies';
import { Company } from '../pages/Company';
import { Documents } from '../pages/Documents';
import { Review } from '../pages/Review';
import { withViewTransition } from '../ui/viewTransition';
import { Sidebar, type Page } from './Sidebar';
import styles from './AppShell.module.css';

type Props = { me: S['AdminMe']; onLogout: () => void; onPasswordChanged: () => void };

function usePendingCount(companyId: string) {
  const [pending, setPending] = useState(0);
  useEffect(() => {
    setPending(0);
    if (companyId) api.pendingCards(companyId).then((cards) => setPending(cards.length), () => undefined);
  }, [companyId]);
  return [pending, setPending] as const;
}

export function AppShell({ me, onLogout, onPasswordChanged }: Props) {
  const isGroup = me.role === 'GROUP_ADMIN';
  const [page, setPageNow] = useState<Page>(isGroup ? 'companies' : 'company');
  const [companyId, setCompanyId] = useState(me.companyId ?? '');
  const [openedName, setOpenedName] = useState<string>();
  const [pending, setPending] = usePendingCount(companyId);
  const setPage = (next: Page) => withViewTransition(() => setPageNow(next));
  const open = (id: string, name: string) => { setCompanyId(id); setOpenedName(name); setPage('company'); };
  const companyName = openedName ?? me.companies?.find((c) => c.id === companyId)?.name;
  const needsCompany = page !== 'companies' && page !== 'account';
  return (
    <div className={styles.shell}>
      <Sidebar isGroup={isGroup} name={me.displayName} companyName={companyId ? companyName : undefined} page={page}
        pending={pending} setPage={setPage} onLogout={onLogout} />
      <main className={styles.main}>
        {page === 'account' && <Account me={me} onPasswordChanged={onPasswordChanged} />}
        {page === 'companies' && <Companies onOpen={open} />}
        {needsCompany && !companyId && <p>Elige primero una empresa en «Empresas».</p>}
        {needsCompany && companyId && page === 'company' && <Company key={companyId} companyId={companyId} me={me} />}
        {needsCompany && companyId && page === 'documents' && <Documents key={companyId} companyId={companyId} />}
        {needsCompany && companyId && page === 'review' && <Review key={companyId} companyId={companyId} onPendingChanged={setPending} />}
      </main>
    </div>
  );
}
