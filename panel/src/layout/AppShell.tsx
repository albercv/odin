import { useEffect, useState } from 'react';
import { api, type S } from '../api/client';
import { useLoad } from '../components/useLoad';
import { Account } from '../pages/Account';
import { Companies } from '../pages/Companies';
import { Company } from '../pages/Company';
import { CaseFile } from '../pages/CaseFile';
import { Documents } from '../pages/Documents';
import { Gaps } from '../pages/Gaps';
import { GroupAdmins } from '../pages/GroupAdmins';
import { Review } from '../pages/Review';
import { Topics } from '../pages/Topics';
import { Usage } from '../pages/Usage';
import { withViewTransition } from '../ui/viewTransition';
import { Sidebar, type Page } from './Sidebar';
import styles from './AppShell.module.css';

/** `/casos/:caseId?empresa=:companyId` opens the case screen; a group admin has no default company, so the link carries it. */
const caseRoute = () => ({ caseId: /^\/casos\/([^/]+)$/.exec(location.pathname)?.[1], companyId: new URLSearchParams(location.search).get('empresa') ?? '' });

function useCaseRoute() {
  const [route, setRoute] = useState(caseRoute);
  const leave = () => { if (route.caseId) { history.pushState(null, '', '/'); setRoute({ caseId: undefined, companyId: '' }); } };
  return { route, leave };
}

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

type CompanyPageProps = { page: Page; companyId: string; me: S['AdminMe']; onPendingChanged: (count: number) => void };

/** Everything inside the chosen company; `key` makes each company start from a clean screen. */
function CompanyPage({ page, companyId, me, onPendingChanged }: CompanyPageProps) {
  return (
    <>
      {page === 'company' && <Company key={companyId} companyId={companyId} me={me} />}
      {page === 'documents' && <Documents key={companyId} companyId={companyId} scannedDocumentsSupported={me.scannedDocumentsSupported} />}
      {page === 'topics' && <Topics key={companyId} companyId={companyId} />}
      {page === 'review' && <Review key={companyId} companyId={companyId} onPendingChanged={onPendingChanged} />}
      {page === 'usage' && <Usage key={companyId} companyId={companyId} />}
      {page === 'gaps' && <Gaps key={companyId} companyId={companyId} />}
    </>
  );
}

export function AppShell({ me, onLogout, onPasswordChanged, onProfileChanged }: Props) {
  const isGroup = me.role === 'GROUP_ADMIN';
  const { route, leave } = useCaseRoute();
  const [page, setPageNow] = useState<Page>(isGroup && !route.caseId ? 'companies' : 'company');
  const [companyId, setCompanyId] = useState(me.companyId ?? (isGroup ? route.companyId : me.companies[0]?.id ?? ''));
  const [pending, setPending] = usePendingCount(companyId);
  const groupCompanies = useGroupCompanies(isGroup);
  const companies: S['CompanyRef'][] = isGroup ? groupCompanies.data ?? [] : me.companies;
  const setPage = (next: Page) => withViewTransition(() => { leave(); setPageNow(next); });
  const open = (id: string) => { setCompanyId(id); setPage('company'); };
  const companyName = companies.find((c) => c.id === companyId)?.name;
  const needsCompany = page !== 'companies' && page !== 'account' && page !== 'groupAdmins';
  return (
    <div className={styles.shell}>
      <Sidebar isGroup={isGroup} name={me.displayName} companyName={companyId ? companyName : undefined} page={page}
        switcher={{ companies, currentId: companyId, onChange: isGroup ? open : setCompanyId }}
        pending={pending} setPage={setPage} onLogout={onLogout} />
      <main className={styles.main}>
        {!route.caseId && page === 'account' && <Account me={me} onPasswordChanged={onPasswordChanged} onProfileChanged={onProfileChanged} />}
        {!route.caseId && page === 'groupAdmins' && isGroup && <GroupAdmins me={me} />}
        {!route.caseId && page === 'companies' && <Companies onOpen={open} onChanged={groupCompanies.reload} />}
        {needsCompany && !companyId && <p>Elige una empresa en el menú.</p>}
        {route.caseId && companyId && <CaseFile key={route.caseId} companyId={companyId} caseId={route.caseId} />}
        {!route.caseId && needsCompany && companyId && <CompanyPage page={page} companyId={companyId} me={me} onPendingChanged={setPending} />}
      </main>
    </div>
  );
}
