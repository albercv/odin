import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, type S } from '../api/client';
import { useLoad } from '../components/useLoad';
import { Activity } from '../pages/Activity';
import { Account } from '../pages/Account';
import { Companies } from '../pages/Companies';
import { Company } from '../pages/Company';
import { CaseFile } from '../pages/CaseFile';
import { Documents } from '../pages/Documents';
import { Gaps } from '../pages/Gaps';
import { GroupAdmins } from '../pages/GroupAdmins';
import { Intake } from '../pages/Intake';
import { Review } from '../pages/Review';
import { Topics } from '../pages/Topics';
import { Usage } from '../pages/Usage';
import { EmptyState } from '../ui/EmptyState';
import { Notice } from '../components/Notice';
import { ProjectTermsContext, projectTerms } from '../components/projectTerms';
import { withViewTransition } from '../ui/viewTransition';
import { Sidebar, type Page } from './Sidebar';
import styles from './AppShell.module.css';

/** `/casos/:caseId?empresa=:companyId` opens the case screen; a group admin has no default company, so the link carries it. */
const caseRoute = () => ({ caseId: /^\/casos\/([^/]+)$/.exec(location.pathname)?.[1], companyId: new URLSearchParams(location.search).get('empresa') ?? '' });

/** The company of a case link, when this admin may act in it. */
const linkedCompany = (me: S['AdminMe'], companyId: string) => (me.role === 'GROUP_ADMIN' || me.companies.some((c) => c.id === companyId) ? companyId : '');

/** The route follows the URL, also on Back and Forward. */
function useCaseRoute(onCompany: (companyId: string) => void) {
  const [route, setRoute] = useState(caseRoute);
  useEffect(() => {
    const sync = () => { const next = caseRoute(); setRoute(next); if (next.companyId) onCompany(next.companyId); };
    window.addEventListener('popstate', sync);
    return () => window.removeEventListener('popstate', sync);
  }, [onCompany]);
  const leave = () => { if (route.caseId) { history.pushState(null, '', '/'); setRoute({ caseId: undefined, companyId: '' }); } };
  return { route, leave };
}

function useCompanyId(me: S['AdminMe']) {
  const [companyId, setCompanyId] = useState(() => linkedCompany(me, caseRoute().companyId) || me.companyId || (me.role === 'GROUP_ADMIN' ? '' : me.companies[0]?.id ?? ''));
  const onLinkedCompany = useCallback((id: string) => { const own = linkedCompany(me, id); if (own) setCompanyId(own); }, [me]);
  return { companyId, setCompanyId, onLinkedCompany };
}

type Props = { me: S['AdminMe']; onLogout: () => void; onPasswordChanged: () => void; onProfileChanged: () => void };

function usePendingCount(companyId: string) {
  const [pending, setPending] = useState(0);
  useEffect(() => {
    setPending(0);
    if (companyId) api.pendingCount(companyId).then(setPending, () => undefined);
  }, [companyId]);
  return [pending, setPending] as const;
}

/** The group admin's list comes from the companies API: `me.companies` is fixed at login. */
function useGroupCompanies(isGroup: boolean) {
  return useLoad(() => (isGroup ? api.companies() : Promise.resolve<S['CompanyView'][]>([])), [isGroup]);
}

const needsCompany = (page: Page) => page !== 'companies' && page !== 'account' && page !== 'groupAdmins' && page !== 'activity';

/** The chosen company's name and its word for «project»; recomputed only when the list or the choice changes. */
function useCurrentCompany(companies: S['CompanyRef'][], companyId: string) {
  const current = companies.find((c) => c.id === companyId);
  const terms = useMemo(() => projectTerms(current?.projectLabel), [current?.projectLabel]);
  return { companies, companyName: current?.name, terms };
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
      {page === 'intake' && <Intake key={companyId} companyId={companyId} />}
    </>
  );
}

/** Pages that span companies: the activity log (both kinds of admin) and the group's administration. */
function AdminPages({ page, me, companies }: { page: Page; me: S['AdminMe']; companies: S['CompanyRef'][] }) {
  return (
    <>
      {page === 'activity' && <Activity me={me} companies={companies} />}
      {page === 'groupAdmins' && me.role === 'GROUP_ADMIN' && <GroupAdmins me={me} />}
    </>
  );
}

function NoCompany({ loading, failed }: { loading: boolean; failed: boolean }) {
  if (failed) return <Notice message="No se han podido cargar las empresas. Vuelve a intentarlo en un momento." />;
  if (loading) return <p>Cargando…</p>;
  return <EmptyState title="Todavía no hay empresas" description="Crea la primera en la pestaña Grupo." />;
}

export function AppShell({ me, onLogout, onPasswordChanged, onProfileChanged }: Props) {
  const isGroup = me.role === 'GROUP_ADMIN';
  const { companyId: chosenId, setCompanyId, onLinkedCompany } = useCompanyId(me);
  const { route, leave } = useCaseRoute(onLinkedCompany);
  const groupCompanies = useGroupCompanies(isGroup);
  // A group admin starts on the first company of the list, once it has loaded.
  const companyId = chosenId || (isGroup ? groupCompanies.data?.[0]?.id ?? '' : '');
  const [page, setPageNow] = useState<Page>(isGroup && !route.caseId ? 'companies' : 'company');
  const [pending, setPending] = usePendingCount(companyId);
  const setPage = (next: Page) => withViewTransition(() => { leave(); setPageNow(next); });
  const open = (id: string) => { setCompanyId(id); setPage('company'); };
  const change = (id: string) => { if (route.caseId) { leave(); setPageNow('documents'); } return isGroup ? open(id) : setCompanyId(id); };
  const { companies, companyName, terms } = useCurrentCompany(isGroup ? groupCompanies.data ?? [] : me.companies, companyId);
  return (
    <ProjectTermsContext.Provider value={terms}>
      <div className={styles.shell}>
        <Sidebar isGroup={isGroup} name={me.displayName} companyName={companyId ? companyName : undefined} page={page}
          switcher={{ companies, currentId: companyId, onChange: change }}
          pending={pending} setPage={setPage} onLogout={onLogout} />
        <main className={styles.main}>
          {!route.caseId && page === 'account' && <Account me={me} onPasswordChanged={onPasswordChanged} onProfileChanged={onProfileChanged} />}
          {!route.caseId && <AdminPages page={page} me={me} companies={companies} />}
          {!route.caseId && page === 'companies' && <Companies onOpen={open} onChanged={groupCompanies.reload} />}
          {needsCompany(page) && !companyId && <NoCompany loading={isGroup && !groupCompanies.data && !groupCompanies.error} failed={isGroup && !!groupCompanies.error} />}
          {route.caseId && companyId && <CaseFile key={route.caseId} companyId={companyId} caseId={route.caseId} />}
          {!route.caseId && needsCompany(page) && companyId && <CompanyPage page={page} companyId={companyId} me={me} onPendingChanged={setPending} />}
        </main>
      </div>
    </ProjectTermsContext.Provider>
  );
}
