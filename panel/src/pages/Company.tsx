import { useState } from 'react';
import { api, type S } from '../api/client';
import { Capabilities } from '../components/Capabilities';
import { Members } from '../components/Members';
import { Notice } from '../components/Notice';
import { Projects } from '../components/Projects';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { PageHeader } from '../ui/PageHeader';

export function Company({ companyId, me }: { companyId: string; me: S['AdminMe'] }) {
  const companies = useLoad(api.companies, []);
  const [adding, setAdding] = useState(false);
  const { error, act } = useAction();
  const company = companies.data?.find((c) => c.id === companyId);
  const isGroup = me.role === 'GROUP_ADMIN';
  const change = async (capabilities: Parameters<typeof api.setCapabilities>[1]) => (await act(() => api.setCapabilities(companyId, capabilities))) && companies.reload();
  return (
    <div>
      <PageHeader title={company?.name ?? 'Empresa'}
        actions={<Button variant="primary" icon="plus" onClick={() => setAdding(true)}>Añadir persona</Button>} />
      <Notice message={error ?? companies.error} />
      {company && <Capabilities company={company} onChange={isGroup ? change : undefined} />}
      <Projects companyId={companyId} />
      <Members companyId={companyId} me={me} addOpen={adding} onAddClose={() => setAdding(false)} />
    </div>
  );
}
