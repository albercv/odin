import { useEffect, useState } from 'react';
import { api, type S } from '../api/client';
import { useLoad } from '../components/useLoad';
import { Account } from '../pages/Account';
import { Companies } from '../pages/Companies';
import { Company } from '../pages/Company';
import { Documents } from '../pages/Documents';
import { GroupAdmins } from '../pages/GroupAdmins';
import { Review } from '../pages/Review';
import { withViewTransition } from '../ui/viewTransition';
import { Sidebar, type Page } from './Sidebar';
import styles from './AppShell.module.css';

type Props = { me: S['AdminMe']; onLogout: () => void; onPasswordChanged: () => void; onProfileChanged: () => void };

function usePendingCount(companyId: string) {
  const [pending, setPending] = useState(0);
  useEffect(() => {
    setPending(0);
    if (companyId) api.pendingCards(companyId).then((cards) => setPending(cards.length), () => undefined);
  }, [companyId]);
  return [pending, setPending] as const;
}

/** The group admin's list comes from the companies API: `me.companies` is fixed at login. */
function useGroupCompanies(isGroup: boolean) {
  return useLoad(() => (isGroup ? api.companies() : Promise.resolve<S['CompanyView'][]>([])), [isGroup]);
}

export function AppShell({ me, onLogout, onPasswordChanged, onProfileChanged }: Props) {
  const isGroup = me.role === 'GROUP_ADMIN';
  const [page, setPageNow] = useState<Page>(isGroup ? 'companies' : 'company');
  const [companyId, setCompanyId] = useState(me.companyId ?? (isGroup ? '' : me.companies[0]?.id ?? ''));
  const [pending, setPending] = usePendingCount(companyId);
  const groupCompanies = useGroupCompanies(isGroup);
  const companies: S['CompanyRef'][] = isGroup ? groupCompanies.data ?? [] : me.companies;
  const setPage = (next: Page) => withViewTransition(() => setPageNow(next));
  const open = (id: string) => { setCompanyId(id); setPage('company'); };
  const companyName = companies.find((c) => c.id === companyId)?.name;
  const needsCompany = page !== 'companies' && page !== 'account' && page !== 'groupAdmins';
  return (
    <div className={styles.shell}>
      <Sidebar isGroup={isGroup} name={me.displayName} companyName={companyId ? companyName : undefined} page={page}
        switcher={{ companies, currentId: companyId, onChange: isGroup ? open : setCompanyId }}
        pending={pending} setPage={setPage} onLogout={onLogout} />
      <main className={styles.main}>
        {page === 'account' && <Account me={me} onPasswordChanged={onPasswordChanged} onProfileChanged={onProfileChanged} />}
        {page === 'groupAdmins' && isGroup && <GroupAdmins me={me} />}
        {page === 'companies' && <Companies onOpen={open} onChanged={groupCompanies.reload} />}
        {needsCompany && !companyId && <p>Elige una empresa en el menú.</p>}
        {needsCompany && companyId && page === 'company' && <Company key={companyId} companyId={companyId} me={me} />}
        {needsCompany && companyId && page === 'documents' && <Documents key={companyId} companyId={companyId} scannedDocumentsSupported={me.scannedDocumentsSupported} />}
        {needsCompany && companyId && page === 'review' && <Review key={companyId} companyId={companyId} onPendingChanged={setPending} />}
      </main>
    </div>
  );
}
