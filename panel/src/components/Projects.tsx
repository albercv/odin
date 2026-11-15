import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { Pagination } from '../ui/Pagination';
import { SearchBox } from '../ui/SearchBox';
import { Table, Td, Tr, tableStyles } from '../ui/Table';
import { NameDialog } from './NameDialog';
import { Notice } from './Notice';
import { ProjectMembers, whoSees } from './ProjectMembers';
import { RenameForm } from './RenameForm';
import { useAction, useLoad } from './useLoad';
import { useProjectTerms } from './projectTerms';
import { includesText, usePagedList } from './usePagedList';

type Panel = 'rename' | 'access' | undefined;
type RowProps = { project: S['ProjectView']; companyId: string; onChanged: () => void };

function ProjectActions({ project, companyId, onChanged, panel, show }: RowProps & { panel: Panel; show: (p: Panel) => void }) {
  const { error, act } = useAction();
  const confirm = useConfirm();
  const t = useProjectTerms();
  const remove = async () => {
    if (!(await confirm({ message: `¿Borrar ${t.the} ${t.one} ${project.name}? Sus documentos se conservan.`, confirmLabel: 'Borrar', danger: true }))) return;
    if (await act(() => api.deleteProject(companyId, project.id))) onChanged();
  };
  return (
    <>
      <RowActions label={`Más acciones de ${project.name}`}>
        <RowAction variant="ghost" text="Renombrar" target={project.name} onClick={() => show('rename')} />
        <RowAction variant="ghost" text="Miembros" label="Miembros de" target={project.name} aria-expanded={panel === 'access'} onClick={() => show('access')} />
        <RowAction variant="danger" text="Borrar" target={project.name} onClick={remove} />
      </RowActions>
      <Notice message={error} />
    </>
  );
}

function ProjectRow(props: RowProps) {
  const { project, companyId, onChanged } = props;
  const [panel, setPanel] = useState<Panel>();
  const show = (p: Panel) => setPanel(panel === p ? undefined : p);
  const t = useProjectTerms();
  return (
    <>
      <Tr>
        <Td label={t.One}>{project.name}</Td>
        <Td label={t.agree('Quién lo ve', 'Quién la ve')}>{whoSees(project)}</Td>
        <Td className={tableStyles.actions}><ProjectActions {...props} panel={panel} show={show} /></Td>
      </Tr>
      {panel && (
        <Tr className={tableStyles.panelRow}>
          <Td colSpan={3}>{panel === 'rename'
            ? <RenameForm name={project.name} label="Nombre" onSave={(name) => api.updateProject(companyId, project.id, { name })} onSaved={() => { setPanel(undefined); onChanged(); }} onCancel={() => setPanel(undefined)} />
            : <ProjectMembers companyId={companyId} project={project} onChanged={onChanged} />}</Td>
        </Tr>
      )}
    </>
  );
}

const NONE: S['ProjectView'][] = [];
const byName = (p: S['ProjectView'], q: string) => includesText(p.name, q);

/** `version` changes when something elsewhere (a person's accesses, a removal) altered the project members. */
export function Projects({ companyId, version = 0 }: { companyId: string; version?: number }) {
  const list = useLoad(() => api.projects(companyId), [companyId, version]);
  const [adding, setAdding] = useState(false);
  const paged = usePagedList(list.data ?? NONE, byName, 10);
  const t = useProjectTerms();
  return (
    <Section title={t.Many} description={`${t.AOne} ${t.agree('nuevo', 'nueva')} nace ${t.agree('cerrado', 'cerrada')}: solo ${t.agree('lo', 'la')} ven sus miembros.`}
      actions={<Button icon="plus" onClick={() => setAdding(true)}>{t.newOne}</Button>}>
      <Notice message={list.error} />
      <SearchBox label={`Buscar ${t.many}`} onSearch={paged.setQuery} resultCount={paged.total} />
      <Table columns={[t.One, t.agree('Quién lo ve', 'Quién la ve')]}>
        {paged.visible.map((p) => <ProjectRow key={p.id} project={p} companyId={companyId} onChanged={list.reload} />)}
      </Table>
      <Pagination label={`Páginas de ${t.many}`} page={paged.page} pageCount={paged.pageCount} onPage={paged.setPage} />
      <NameDialog title={t.newOne} label={`Nombre ${t.agree('del', 'de la')} ${t.one}`} submitLabel={`Crear ${t.one}`} open={adding}
        onSubmit={(name) => api.createProject(companyId, name)} onClose={() => setAdding(false)} onDone={list.reload} />
    </Section>
  );
}
