import { useState } from 'react';
import { api, type S } from '../api/client';
import { Capabilities } from '../components/Capabilities';
import { NameDialog } from '../components/NameDialog';
import { Notice } from '../components/Notice';
import { RenameForm } from '../components/RenameForm';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { PageHeader } from '../ui/PageHeader';
import { RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { Table, tableStyles } from '../ui/Table';

function CompanyRow({ c, onOpen, onDone }: { c: S['CompanyView']; onOpen: () => void; onDone: () => void }) {
  const [renaming, setRenaming] = useState(false);
  const { error, act } = useAction();
  const run = async (fn: () => Promise<unknown>) => (await act(fn)) && onDone();
  return (
    <tr>
      <td>
        {renaming
          ? <RenameForm name={c.name} label={`Nombre de ${c.name}`} onSave={(n) => api.renameCompany(c.id, n)} onSaved={() => { setRenaming(false); onDone(); }} onCancel={() => setRenaming(false)} />
          : <strong>{c.name}</strong>}
        <Notice message={error} />
      </td>
      <td><Capabilities company={c} onChange={(caps) => run(() => api.setCapabilities(c.id, caps))} /></td>
      <td className={tableStyles.actions}>
        <Button onClick={onOpen}>Abrir {c.name}</Button>
        <RowActions label={`Más acciones de ${c.name}`}><Button variant="ghost" onClick={() => setRenaming(true)}>Renombrar {c.name}</Button></RowActions>
      </td>
    </tr>
  );
}

export function Companies({ onOpen }: { onOpen: (id: string, name: string) => void }) {
  const list = useLoad(api.companies, []);
  const [adding, setAdding] = useState(false);
  return (
    <div>
      <PageHeader title="Empresas" actions={<Button variant="primary" icon="plus" onClick={() => setAdding(true)}>Nueva empresa</Button>} />
      <Notice message={list.error} />
      <Section title="Empresas del grupo">
        <Table columns={['Empresa', 'Capacidades']}>
          {list.data?.map((c) => <CompanyRow key={c.id} c={c} onOpen={() => onOpen(c.id, c.name)} onDone={list.reload} />)}
        </Table>
      </Section>
      <NameDialog title="Nueva empresa" label="Nombre de la empresa" submitLabel="Crear empresa" codeLabel="Código" open={adding}
        onSubmit={api.createCompany} onClose={() => setAdding(false)} onDone={list.reload} />
    </div>
  );
}
