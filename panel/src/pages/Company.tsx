import { api, type S } from '../api/client';
import { Projects } from '../components/Projects';
import { Users } from '../components/Users';
import { useLoad } from '../components/useLoad';

export function Company({ companyId, me }: { companyId: string; me: S['AdminMe'] }) {
  const companies = useLoad(api.companies, []);
  const name = companies.data?.find((c) => c.id === companyId)?.name ?? 'Empresa';
  return (
    <div>
      <h1>{name}</h1>
      <Projects companyId={companyId} />
      <Users companyId={companyId} me={me} />
    </div>
  );
}
